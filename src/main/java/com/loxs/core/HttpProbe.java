package com.loxs.core;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public final class HttpProbe {
    private static final int MAX_BODY_BYTES = 1_500_000;
    private static final String[] USER_AGENTS = {
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36",
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 13_0) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/16.0 Safari/605.1.15"
    };

    private final HttpClient client;
    private final Duration timeout;
    private final String cookie;
    private final String userAgent;

    public HttpProbe(Duration timeout, String cookie, boolean verifyTls) {
        this.timeout = timeout;
        this.cookie = cookie;
        this.userAgent = USER_AGENTS[ThreadLocalRandom.current().nextInt(USER_AGENTS.length)];
        this.client = buildClient(timeout, verifyTls);
    }

    private static HttpClient buildClient(Duration timeout, boolean verifyTls) {
        HttpClient.Builder builder = HttpClient.newBuilder()
                .connectTimeout(timeout)
                .followRedirects(HttpClient.Redirect.NEVER)
                .version(HttpClient.Version.HTTP_1_1);
        if (!verifyTls) {
            try {
                TrustManager[] trustAll = new TrustManager[]{
                        new X509TrustManager() {
                            @Override
                            public void checkClientTrusted(X509Certificate[] chain, String authType) {
                            }

                            @Override
                            public void checkServerTrusted(X509Certificate[] chain, String authType) {
                            }

                            @Override
                            public X509Certificate[] getAcceptedIssuers() {
                                return new X509Certificate[0];
                            }
                        }
                };
                SSLContext context = SSLContext.getInstance("TLS");
                context.init(null, trustAll, new SecureRandom());
                builder.sslContext(context);
            } catch (Exception exception) {
                throw new IllegalStateException("TLS ayarı oluşturulamadı.", exception);
            }
        }
        return builder.build();
    }

    public Response get(String url) {
        long started = System.nanoTime();
        try {
            HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(timeout)
                    .GET()
                    .header("User-Agent", userAgent)
                    .header("Accept", "*/*")
                    .header("Accept-Encoding", "identity");
            if (cookie != null && !cookie.isBlank()) {
                request.header("Cookie", cookie);
            }
            HttpResponse<InputStream> response = client.send(request.build(), HttpResponse.BodyHandlers.ofInputStream());
            String body;
            try (InputStream stream = response.body()) {
                byte[] bytes = stream.readNBytes(MAX_BODY_BYTES + 1);
                int length = Math.min(bytes.length, MAX_BODY_BYTES);
                body = new String(bytes, 0, length, StandardCharsets.UTF_8);
            }
            long elapsed = (System.nanoTime() - started) / 1_000_000;
            return new Response(response.statusCode(), copyHeaders(response.headers().map()), body, elapsed, false, null);
        } catch (HttpTimeoutException exception) {
            return new Response(0, Map.of(), "", (System.nanoTime() - started) / 1_000_000, true, "Zaman aşımı");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return new Response(0, Map.of(), "", (System.nanoTime() - started) / 1_000_000, false, "İptal edildi");
        } catch (IOException | IllegalArgumentException exception) {
            return new Response(0, Map.of(), "", (System.nanoTime() - started) / 1_000_000, false, exception.getMessage());
        }
    }

    private static Map<String, List<String>> copyHeaders(Map<String, List<String>> headers) {
        Map<String, List<String>> copy = new LinkedHashMap<>();
        headers.forEach((key, value) -> copy.put(key, List.copyOf(value)));
        return copy;
    }

    public record Response(
            int statusCode,
            Map<String, List<String>> headers,
            String body,
            long elapsedMs,
            boolean timedOut,
            String error
    ) {
        public String header(String name) {
            for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
                if (entry.getKey().equalsIgnoreCase(name) && !entry.getValue().isEmpty()) {
                    return entry.getValue().get(0);
                }
            }
            return null;
        }

        public String headersText() {
            StringBuilder builder = new StringBuilder();
            headers.forEach((key, values) -> values.forEach(value -> builder.append(key).append(": ").append(value).append('\n')));
            return builder.toString();
        }
    }
}
