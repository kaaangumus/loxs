package com.loxs.core;

import com.loxs.model.Finding;
import com.loxs.model.Probe;
import com.loxs.model.ScanRequest;
import com.loxs.model.ScanType;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ScannerEngine {
    private static final Pattern META_REFRESH = Pattern.compile(
            "(?is)<meta[^>]+http-equiv\\s*=\\s*['\"]?refresh['\"]?[^>]*content\\s*=\\s*['\"][^'\"]*url\\s*=\\s*([^'\";]+)");

    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private final Map<String, Baseline> baselines = new ConcurrentHashMap<>();
    private final Set<String> confirmedSql = ConcurrentHashMap.newKeySet();
    private volatile HttpProbe http;
    private volatile ScanRequest request;
    private volatile String marker = "";

    public void cancel() {
        cancelled.set(true);
    }

    public boolean isCancelled() {
        return cancelled.get();
    }

    public void scan(ScanRequest scanRequest, ScanListener listener) {
        this.request = scanRequest;
        this.cancelled.set(false);
        this.baselines.clear();
        this.confirmedSql.clear();
        this.marker = "loxs" + Long.toHexString(System.nanoTime()).toLowerCase();
        this.http = new HttpProbe(scanRequest.timeout(), scanRequest.cookie(), scanRequest.verifyTls());

        List<String> targets = normalizeTargets(scanRequest.targets());
        if (targets.isEmpty()) {
            listener.log("Geçerli hedef URL bulunamadı.");
            return;
        }
        if (scanRequest.type() != ScanType.CRLF && scanRequest.payloads().isEmpty()) {
            listener.log("Payload listesi boş.");
            return;
        }

        List<Probe> probes = buildProbes(targets, scanRequest);
        int total = probes.size();
        listener.log(scanRequest.type().label() + " taraması başladı. Hedef: " + targets.size()
                + ", test: " + total + ", thread: " + scanRequest.threads());
        listener.progress(0, total);
        if (total == 0) {
            listener.log("Test edilecek parametre/payload kombinasyonu bulunamadı.");
            return;
        }

        ExecutorService pool = Executors.newFixedThreadPool(scanRequest.threads(), runnable -> {
            Thread thread = new Thread(runnable, "loxs-scanner");
            thread.setDaemon(true);
            return thread;
        });
        CompletionService<Finding> completionService = new ExecutorCompletionService<>(pool);
        Deque<Probe> pending = new ArrayDeque<>(probes);
        int inFlight = 0;
        int completed = 0;
        int maxInFlight = Math.max(2, scanRequest.threads() * 2);
        long started = System.nanoTime();

        try {
            while ((!pending.isEmpty() || inFlight > 0) && !cancelled.get()) {
                while (inFlight < maxInFlight && !pending.isEmpty() && !cancelled.get()) {
                    Probe probe = pending.poll();
                    completionService.submit(() -> scanProbe(probe));
                    inFlight++;
                }
                if (inFlight == 0) {
                    break;
                }
                Future<Finding> future = completionService.poll(200, TimeUnit.MILLISECONDS);
                if (future == null) {
                    continue;
                }
                inFlight--;
                completed++;
                try {
                    Finding finding = future.get();
                    listener.finding(finding);
                } catch (ExecutionException exception) {
                    listener.log("Tarama hatası: " + rootMessage(exception));
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    break;
                }
                listener.progress(completed, total);
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        } finally {
            if (cancelled.get()) {
                pool.shutdownNow();
            } else {
                pool.shutdown();
            }
            try {
                pool.awaitTermination(cancelled.get() ? 2 : 10, TimeUnit.SECONDS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        }

        long seconds = (System.nanoTime() - started) / 1_000_000_000L;
        if (cancelled.get()) {
            listener.log("Tarama durduruldu. Tamamlanan test: " + completed + "/" + total
                    + ", süre: " + seconds + " sn.");
        } else {
            listener.log("Tarama tamamlandı. Test: " + completed + "/" + total
                    + ", süre: " + seconds + " sn.");
        }
        listener.progress(completed, total);
    }

    private List<String> normalizeTargets(List<String> rawTargets) {
        Set<String> unique = new LinkedHashSet<>();
        for (String raw : rawTargets) {
            String normalized = UrlInjector.normalize(raw);
            if (!normalized.isBlank()) {
                unique.add(normalized);
            }
        }
        return List.copyOf(unique);
    }

    private List<Probe> buildProbes(List<String> targets, ScanRequest scanRequest) {
        List<Probe> probes = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        ScanType type = scanRequest.type();

        for (String target : targets) {
            switch (type) {
                case SQLI -> {
                    String base = UrlInjector.ensureParameter(target, "id");
                    for (String parameter : UrlInjector.parameterNames(base)) {
                        String original = UrlInjector.originalValue(base, parameter);
                        for (String payload : scanRequest.payloads()) {
                            String value = original + payload;
                            addProbe(probes, seen, new Probe(type,
                                    UrlInjector.replaceParameter(base, parameter, value),
                                    parameter, payload, target));
                        }
                    }
                }
                case LFI -> {
                    String base = UrlInjector.ensureParameter(target, "file");
                    for (String parameter : UrlInjector.parameterNames(base)) {
                        for (String payload : scanRequest.payloads()) {
                            addProbe(probes, seen, new Probe(type,
                                    UrlInjector.replaceParameterRaw(base, parameter, payload),
                                    parameter, payload, target));
                        }
                    }
                }
                case XSS -> {
                    String base = UrlInjector.ensureParameter(target, "q");
                    for (String parameter : UrlInjector.parameterNames(base)) {
                        for (String template : scanRequest.payloads()) {
                            String payload = template.replace("{MARKER}", marker);
                            addProbe(probes, seen, new Probe(type,
                                    UrlInjector.replaceParameter(base, parameter, payload),
                                    parameter, payload, target));
                        }
                    }
                }
                case CRLF -> {
                    List<String> payloads = crlfPayloads();
                    for (String parameter : UrlInjector.parameterNames(target)) {
                        for (String payload : payloads) {
                            addProbe(probes, seen, new Probe(type,
                                    UrlInjector.replaceParameterRaw(target, parameter, payload),
                                    parameter, payload, target));
                        }
                    }
                    for (String payload : payloads) {
                        addProbe(probes, seen, new Probe(type,
                                UrlInjector.appendToPath(target, payload),
                                "(path)", payload, target));
                    }
                }
                case OPEN_REDIRECT -> {
                    List<String> parameters = UrlInjector.parameterNames(target);
                    if (parameters.isEmpty()) {
                        for (String payload : scanRequest.payloads()) {
                            addProbe(probes, seen, new Probe(type,
                                    UrlInjector.replaceParameter(target, "next", payload),
                                    "next", payload, target));
                            addProbe(probes, seen, new Probe(type,
                                    UrlInjector.appendToPath(target, payload),
                                    "(path)", payload, target));
                        }
                    } else {
                        for (String parameter : parameters) {
                            for (String payload : scanRequest.payloads()) {
                                addProbe(probes, seen, new Probe(type,
                                        UrlInjector.replaceParameter(target, parameter, payload),
                                        parameter, payload, target));
                            }
                        }
                    }
                }
            }
        }
        return probes;
    }

    private List<String> crlfPayloads() {
        if (request.payloads() != null && !request.payloads().isEmpty()) {
            List<String> result = new ArrayList<>();
            for (String template : request.payloads()) {
                String value = template.replace("{MARKER}", marker);
                if (!value.contains(marker)) {
                    value = value + marker;
                }
                result.add(value);
            }
            return result;
        }
        List<String> result = new ArrayList<>();
        for (String template : List.of(
                "/%0d%0aX-Loxs-Probe:{MARKER}",
                "/%0aX-Loxs-Probe:{MARKER}",
                "/%0d%0aSet-Cookie:loxs={MARKER}",
                "/%0aSet-Cookie:loxs={MARKER}",
                "/%23%0aX-Loxs-Probe:{MARKER}",
                "/%25%30%61X-Loxs-Probe:{MARKER}",
                "/%E5%98%8D%E5%98%8AX-Loxs-Probe:{MARKER}"
        )) {
            result.add(template.replace("{MARKER}", marker));
        }
        return result;
    }

    private void addProbe(List<Probe> probes, Set<String> seen, Probe probe) {
        String key = probe.type() + "|" + probe.url() + "|" + probe.payload();
        if (seen.add(key)) {
            probes.add(probe);
        }
    }

    private Finding scanProbe(Probe probe) {
        if (cancelled.get()) {
            return clean(probe, 0, 0, "Durduruldu");
        }
        try {
            return switch (probe.type()) {
                case SQLI -> scanSqli(probe);
                case LFI -> scanLfi(probe);
                case CRLF -> scanCrlf(probe);
                case XSS -> scanXss(probe);
                case OPEN_REDIRECT -> scanOpenRedirect(probe);
            };
        } catch (RuntimeException exception) {
            return clean(probe, 0, 0, "Hata: " + rootMessage(exception));
        }
    }

    private Finding scanSqli(Probe probe) {
        String confirmationKey = probe.target() + "|" + probe.parameter();
        if (confirmedSql.contains(confirmationKey)) {
            return clean(probe, 0, 0, "Aynı parametrede SQLi zaten doğrulandı; kalan payload'lar atlandı.");
        }
        Baseline baseline = baseline(probe);
        if (baseline.timedOut()) {
            return clean(probe, 0, baseline.elapsedMs(), "Taban isteği zaman aşımına uğradı; SQLi doğrulanamadı.");
        }
        HttpProbe.Response first = http.get(probe.url());
        if (first.timedOut() || first.statusCode() >= 500) {
            return clean(probe, first.statusCode(), first.elapsedMs(),
                    first.timedOut() ? "İstek zaman aşımına uğradı." : "HTTP " + first.statusCode());
        }
        double absoluteThreshold = request.delayThresholdSeconds() * 1000.0;
        double baselineThreshold = baseline.elapsedMs() + request.baselineMarginSeconds() * 1000.0;
        double threshold = Math.max(absoluteThreshold, baselineThreshold);
        if (first.elapsedMs() < threshold) {
            return clean(probe, first.statusCode(), first.elapsedMs(),
                    String.format(java.util.Locale.ROOT, "Gecikme yok (%.0f ms < %.0f ms)",
                            (double) first.elapsedMs(), threshold));
        }

        HttpProbe.Response second = http.get(probe.url());
        boolean confirmed = !second.timedOut()
                && second.statusCode() < 500
                && second.elapsedMs() >= threshold;
        if (!confirmed) {
            return clean(probe, first.statusCode(), first.elapsedMs(),
                    String.format(java.util.Locale.ROOT, "İlk gecikme doğrulanamadı (%.0f ms / %.0f ms)",
                            (double) first.elapsedMs(), (double) second.elapsedMs()));
        }
        confirmedSql.add(confirmationKey);
        return vulnerable(probe, first.statusCode(), first.elapsedMs(),
                String.format(java.util.Locale.ROOT,
                        "Zaman tabanlı SQLi doğrulandı. Taban: %.0f ms, test: %.0f ms, doğrulama: %.0f ms, eşik: %.0f ms",
                        (double) baseline.elapsedMs(), (double) first.elapsedMs(),
                        (double) second.elapsedMs(), threshold));
    }

    private Finding scanLfi(Probe probe) {
        Baseline baseline = baseline(probe);
        HttpProbe.Response response = http.get(probe.url());
        if (response.timedOut()) {
            return clean(probe, 0, response.elapsedMs(), "İstek zaman aşımına uğradı.");
        }
        if (response.statusCode() != 200) {
            return clean(probe, response.statusCode(), response.elapsedMs(), "HTTP " + response.statusCode());
        }
        for (String criterion : request.criteria()) {
            if (criterion == null || criterion.isBlank()) {
                continue;
            }
            if (!matches(response.body(), criterion)) {
                continue;
            }
            if (!baseline.timedOut() && baseline.body() != null && matches(baseline.body(), criterion)) {
                return clean(probe, response.statusCode(), response.elapsedMs(),
                        "Kriter taban yanıtta da mevcut; yanlış pozitif engellendi.");
            }
            return vulnerable(probe, response.statusCode(), response.elapsedMs(),
                    "LFI kriteri eşleşti: " + criterion);
        }
        return clean(probe, response.statusCode(), response.elapsedMs(), "Kriter eşleşmedi.");
    }

    private Finding scanCrlf(Probe probe) {
        HttpProbe.Response response = http.get(probe.url());
        if (response.timedOut()) {
            return clean(probe, 0, response.elapsedMs(), "İstek zaman aşımına uğradı.");
        }
        String headers = response.headersText();
        if (headers.contains(marker)) {
            return vulnerable(probe, response.statusCode(), response.elapsedMs(),
                    "CRLF enjeksiyonu yanıt header'ında doğrulandı.");
        }
        if (response.body().contains(marker) && response.body().contains("HTTP/1.")) {
            return vulnerable(probe, response.statusCode(), response.elapsedMs(),
                    "CRLF enjeksiyonu bölünmüş yanıt gövdesinde doğrulandı.");
        }
        return clean(probe, response.statusCode(), response.elapsedMs(), "Header'da enjeksiyon bulunamadı.");
    }

    private Finding scanXss(Probe probe) {
        HttpProbe.Response response = http.get(probe.url());
        if (response.timedOut()) {
            return clean(probe, 0, response.elapsedMs(), "İstek zaman aşımına uğradı.");
        }
        String contentType = response.header("Content-Type");
        if (contentType != null && !contentType.toLowerCase().contains("html")) {
            return clean(probe, response.statusCode(), response.elapsedMs(), "HTML olmayan yanıt.");
        }
        String payload = probe.payload();
        boolean dangerous = payload.contains("<");
        if (dangerous && response.body().contains(payload)) {
            return vulnerable(probe, response.statusCode(), response.elapsedMs(),
                    "XSS payload'u yanıtta ham olarak yansıdı.");
        }
        return clean(probe, response.statusCode(), response.elapsedMs(), "Ham yansıma bulunamadı.");
    }

    private Finding scanOpenRedirect(Probe probe) {
        Baseline baseline = baseline(probe);
        HttpProbe.Response response = http.get(probe.url());
        if (response.timedOut()) {
            return clean(probe, 0, response.elapsedMs(), "İstek zaman aşımına uğradı.");
        }
        String targetHost = UrlInjector.host(probe.target());
        String expectedHost = UrlInjector.expectedHost(probe.payload());
        String location = response.header("Location");
        if (location != null) {
            URI resolved = resolve(probe.target(), location);
            String locationHost = resolved == null ? null : resolved.getHost();
            if (locationHost != null && !UrlInjector.sameHost(locationHost, targetHost)) {
                if (expectedHost != null && UrlInjector.sameHost(locationHost, expectedHost)) {
                    return vulnerable(probe, response.statusCode(), response.elapsedMs(),
                            "Dış yönlendirme doğrulandı: " + location);
                }
                if (expectedHost == null && baseline.locationHost() != null
                        && !UrlInjector.sameHost(locationHost, baseline.locationHost())) {
                    return vulnerable(probe, response.statusCode(), response.elapsedMs(),
                            "Beklenmeyen dış yönlendirme: " + location);
                }
            }
        }

        Matcher matcher = META_REFRESH.matcher(response.body());
        if (matcher.find()) {
            String metaLocation = matcher.group(1).strip();
            URI resolved = resolve(probe.target(), metaLocation);
            String locationHost = resolved == null ? null : resolved.getHost();
            if (locationHost != null && expectedHost != null
                    && UrlInjector.sameHost(locationHost, expectedHost)
                    && !UrlInjector.sameHost(locationHost, targetHost)) {
                return vulnerable(probe, response.statusCode(), response.elapsedMs(),
                        "Meta refresh ile dış yönlendirme: " + metaLocation);
            }
        }
        return clean(probe, response.statusCode(), response.elapsedMs(), "Dış yönlendirme bulunamadı.");
    }

    private Baseline baseline(Probe probe) {
        String key = probe.type() + "|" + probe.target() + "|" + probe.parameter();
        return baselines.computeIfAbsent(key, ignored -> measureBaseline(probe));
    }

    private Baseline measureBaseline(Probe probe) {
        String benign = switch (probe.type()) {
            case LFI -> {
                String original = UrlInjector.originalValue(probe.target(), probe.parameter());
                yield original.isBlank() ? "index" : original;
            }
            case OPEN_REDIRECT -> {
                String original = UrlInjector.originalValue(probe.target(), probe.parameter());
                yield original.isBlank() ? "local" : original;
            }
            default -> {
                String original = UrlInjector.originalValue(probe.target(), probe.parameter());
                yield original.isBlank() ? "1" : original;
            }
        };
        String url = probe.parameter().equals("(path)")
                ? probe.target()
                : UrlInjector.replaceParameter(probe.target(), probe.parameter(), benign);
        HttpProbe.Response first = http.get(url);
        HttpProbe.Response second = http.get(url);
        long elapsed = Math.min(first.elapsedMs(), second.elapsedMs());
        boolean timedOut = first.timedOut() && second.timedOut();
        String body = first.body() != null && !first.body().isEmpty() ? first.body() : second.body();
        String location = first.header("Location") != null ? first.header("Location") : second.header("Location");
        String locationHost = null;
        if (location != null) {
            URI resolved = resolve(probe.target(), location);
            locationHost = resolved == null ? null : resolved.getHost();
        }
        return new Baseline(first.statusCode(), elapsed, body, timedOut, locationHost);
    }

    private URI resolve(String base, String location) {
        try {
            return URI.create(base).resolve(location);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private boolean matches(String body, String criterion) {
        if (body == null) {
            return false;
        }
        try {
            return Pattern.compile(criterion).matcher(body).find();
        } catch (Exception exception) {
            return body.contains(criterion);
        }
    }

    private Finding vulnerable(Probe probe, int status, long elapsed, String details) {
        return new Finding(probe.type(), true, probe.url(), probe.parameter(), probe.payload(),
                status, elapsed, details, Instant.now());
    }

    private Finding clean(Probe probe, int status, long elapsed, String details) {
        return new Finding(probe.type(), false, probe.url(), probe.parameter(), probe.payload(),
                status, elapsed, details, Instant.now());
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }

    private record Baseline(int statusCode, long elapsedMs, String body, boolean timedOut, String locationHost) {
    }
}
