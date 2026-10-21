package com.loxs.report;

import com.loxs.AppInfo;
import com.loxs.model.Finding;
import com.loxs.model.ScanType;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

public final class ReportWriter {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());
    private static final int MAX_CLEAN_ROWS = 2_000;

    private ReportWriter() {
    }

    public static void writeHtml(Path path, ScanType scanType, int targetCount,
                                 List<Finding> findings, int totalTests) throws IOException {
        writeHtml(path, scanType.label(), targetCount, findings, totalTests);
    }

    public static void writeHtml(Path path, String scanLabel, int targetCount,
                                 List<Finding> findings, int totalTests) throws IOException {
        long vulnerable = findings.stream().filter(Finding::vulnerable).count();
        long clean = findings.size() - vulnerable;
        String rows = buildRows(findings);
        String html = """
                <!doctype html>
                <html lang="tr">
                <head>
                  <meta charset="utf-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1">
                  <title>LOXS Tarama Raporu</title>
                  <style>
                    :root{color-scheme:dark;--bg:#0b1120;--panel:#111a2e;--panel2:#17233d;--text:#e5edf8;
                    --muted:#93a4bd;--border:#2b3b58;--accent:#38bdf8;--ok:#34d399;--bad:#f87171}
                    *{box-sizing:border-box}body{margin:0;background:var(--bg);color:var(--text);
                    font-family:"Segoe UI",Arial,sans-serif;line-height:1.5}
                    main{max-width:1250px;margin:0 auto;padding:32px 22px 60px}
                    h1{margin:0;color:var(--accent);font-size:30px}h2{margin:28px 0 12px;font-size:19px}
                    .sub{color:var(--muted);margin-top:4px}.cards{display:grid;gap:12px;
                    grid-template-columns:repeat(auto-fit,minmax(160px,1fr));margin-top:22px}
                    .card{background:var(--panel);border:1px solid var(--border);border-radius:10px;padding:14px}
                    .card small{display:block;color:var(--muted);text-transform:uppercase;font-size:11px;letter-spacing:.08em}
                    .card strong{display:block;font-size:24px;margin-top:6px}.ok{color:var(--ok)}.bad{color:var(--bad)}
                    table{width:100%%;border-collapse:collapse;table-layout:fixed;background:var(--panel);
                    border:1px solid var(--border);border-radius:10px;overflow:hidden}
                    th,td{padding:10px;border-bottom:1px solid var(--border);text-align:left;vertical-align:top;
                    word-break:break-word;font-size:13px}th{color:var(--muted);font-size:11px;text-transform:uppercase}
                    a{color:var(--accent);text-decoration:none}a:hover{text-decoration:underline}
                    .tag{display:inline-block;padding:3px 8px;border-radius:999px;font-size:11px;font-weight:700}
                    .tag.v{background:rgba(52,211,153,.14);color:var(--ok)}
                    .tag.c{background:rgba(147,164,189,.14);color:var(--muted)}
                    .sev{display:inline-block;padding:2px 7px;border-radius:5px;font-size:11px;font-weight:700}
                    .sev.high{background:rgba(248,113,113,.15);color:#fca5a5}
                    .sev.medium{background:rgba(251,191,36,.15);color:#fcd34d}
                    .sev.low{background:rgba(52,211,153,.12);color:#86efac}
                    .meta{background:var(--panel);border:1px solid var(--border);border-radius:10px;
                    padding:14px 16px;margin-top:18px;color:var(--muted);font-size:13px}
                    .meta strong{color:var(--text)}
                    .empty{color:var(--muted);padding:18px;text-align:center}
                  </style>
                </head>
                <body><main>
                  <h1>LOXS Tarama Raporu</h1>
                  <div class="sub">Yetkili güvenlik testi raporu</div>
                  <div class="cards">
                    <div class="card"><small>Tarayıcı</small><strong>%s</strong></div>
                    <div class="card"><small>Hedef</small><strong>%d</strong></div>
                    <div class="card"><small>Toplam Test</small><strong>%d</strong></div>
                    <div class="card"><small>Bulgular</small><strong class="bad">%d</strong></div>
                    <div class="card"><small>Temiz</small><strong class="ok">%d</strong></div>
                    <div class="card"><small>Tarih</small><strong style="font-size:15px">%s</strong></div>
                  </div>
                  <div class="meta">
                    <strong>Kapsam ve metodoloji.</strong>
                    Bu rapor LOXS %s tarafından, kullanıcı tarafından yetkilendirildiği beyan edilen
                    hedeflere karşı üretilmiştir. Toplam %d hedef ve %d test kaydı içerir.
                    Bulgular otomatik doğrulama kurallarıyla üretilmiştir; kritik sistemlerde
                    manuel doğrulama önerilir.
                  </div>
                  <h2>Bulgular</h2>
                  <table>
                    <thead><tr>
                      <th style="width:82px">Durum</th><th style="width:68px">Önem</th>
                      <th style="width:68px">Güven</th><th style="width:95px">Parametre</th>
                      <th style="width:245px">URL</th><th style="width:170px">Payload</th>
                      <th style="width:55px">Süre</th><th style="width:190px">Detay</th>
                      <th style="width:210px">Çözüm</th>
                    </tr></thead>
                    <tbody>%s</tbody>
                  </table>
                </main></body></html>
                """.formatted(
                escape(scanLabel),
                targetCount,
                totalTests,
                vulnerable,
                clean,
                escape(DATE.format(java.time.Instant.now())),
                AppInfo.VERSION,
                targetCount,
                totalTests,
                rows
        );
        Files.writeString(path, html, StandardCharsets.UTF_8);
    }

    private static String buildRows(List<Finding> findings) {
        if (findings.isEmpty()) {
            return "<tr><td colspan=\"9\" class=\"empty\">Sonuç yok.</td></tr>";
        }
        StringBuilder rows = new StringBuilder();
        int cleanRows = 0;
        for (Finding finding : findings) {
            if (!finding.vulnerable()) {
                if (cleanRows >= MAX_CLEAN_ROWS) {
                    continue;
                }
                cleanRows++;
            }
            boolean vulnerable = finding.vulnerable();
            String tag = vulnerable
                    ? "<span class=\"tag v\">VULNERABLE</span>"
                    : "<span class=\"tag c\">Temiz</span>";
            String severity = vulnerable ? severity(finding) : "—";
            String severityClass = severity.equals("Yüksek") ? "high"
                    : severity.equals("Orta") ? "medium" : "low";
            String confidence = vulnerable ? confidence(finding) : "—";
            rows.append("<tr>")
                    .append("<td>").append(tag).append("</td>")
                    .append("<td>").append(vulnerable
                            ? "<span class=\"sev " + severityClass + "\">" + severity + "</span>" : "—")
                    .append("</td>")
                    .append("<td>").append(confidence).append("</td>")
                    .append("<td>").append(escape(finding.parameter())).append("</td>")
                    .append("<td>").append(link(finding.url())).append("</td>")
                    .append("<td>").append(escape(finding.payload())).append("</td>")
                    .append("<td>").append(finding.elapsedMs()).append(" ms</td>")
                    .append("<td>").append(escape(finding.details())).append("</td>")
                    .append("<td>").append(vulnerable ? escape(remediation(finding)) : "—").append("</td>")
                    .append("</tr>");
        }
        if (cleanRows >= MAX_CLEAN_ROWS) {
            rows.append("<tr><td colspan=\"9\" class=\"empty\">Temiz sonuçların yalnızca ilk ")
                    .append(MAX_CLEAN_ROWS).append(" satırı gösteriliyor.</td></tr>");
        }
        return rows.toString();
    }

    private static String severity(Finding finding) {
        return switch (finding.type()) {
            case SQLI, LFI, OPEN_REDIRECT, CRLF -> "Yüksek";
            case XSS -> "Orta";
        };
    }

    private static String confidence(Finding finding) {
        String details = finding.details() == null ? "" : finding.details().toLowerCase();
        return details.contains("doğrulandı") || details.contains("confirmed") ? "Yüksek" : "Orta";
    }

    private static String remediation(Finding finding) {
        return switch (finding.type()) {
            case SQLI -> "Parametreli sorgular / prepared statements kullanın; kullanıcı girdisini sorguya birleştirmeyin.";
            case LFI -> "Dosya yollarını allowlist ile sınırlayın, canonical path kontrolü ve yetki doğrulaması ekleyin.";
            case XSS -> "Çıktıyı bağlama uygun encode edin; Content-Security-Policy ve güvenli şablonlama kullanın.";
            case CRLF -> "Header ve URL girdilerini doğrulayın; satır sonu karakterlerini reddedin.";
            case OPEN_REDIRECT -> "Yönlendirme hedeflerini allowlist ile sınırlayın; dış URL'leri doğrudan kabul etmeyin.";
        };
    }

    public static void writeCsv(Path path, List<Finding> findings) throws IOException {
        StringBuilder csv = new StringBuilder("\uFEFF");
        csv.append("durum,onem,guven,tarayici,url,parametre,payload,http_kodu,sure_ms,detay,cozum\n");
        for (Finding finding : findings) {
            csv.append(finding.vulnerable() ? "VULNERABLE" : "Temiz").append(',')
                    .append(csv(finding.vulnerable() ? severity(finding) : "-")).append(',')
                    .append(csv(finding.vulnerable() ? confidence(finding) : "-")).append(',')
                    .append(csv(finding.type().label())).append(',')
                    .append(csv(finding.url())).append(',')
                    .append(csv(finding.parameter())).append(',')
                    .append(csv(finding.payload())).append(',')
                    .append(finding.statusCode()).append(',')
                    .append(finding.elapsedMs()).append(',')
                    .append(csv(finding.details())).append(',')
                    .append(csv(finding.vulnerable() ? remediation(finding) : "-")).append('\n');
        }
        Files.writeString(path, csv.toString(), StandardCharsets.UTF_8);
    }

    public static void writeJson(Path path, String scanLabel, int targetCount,
                                 List<Finding> findings, int totalTests) throws IOException {
        long vulnerable = findings.stream().filter(Finding::vulnerable).count();
        StringBuilder json = new StringBuilder();
        json.append("{\n")
                .append("  \"tool\": \"LOXS\",\n")
                .append("  \"version\": \"").append(json(AppInfo.VERSION)).append("\",\n")
                .append("  \"scan\": \"").append(json(scanLabel)).append("\",\n")
                .append("  \"generatedAt\": \"").append(json(DATE.format(java.time.Instant.now()))).append("\",\n")
                .append("  \"scope\": {\"targets\": ").append(targetCount)
                .append(", \"totalTests\": ").append(totalTests).append("},\n")
                .append("  \"summary\": {\"vulnerable\": ").append(vulnerable)
                .append(", \"clean\": ").append(findings.size() - vulnerable).append("},\n")
                .append("  \"findings\": [\n");
        boolean first = true;
        for (Finding finding : findings) {
            if (!finding.vulnerable()) {
                continue;
            }
            if (!first) {
                json.append(",\n");
            }
            first = false;
            json.append("    {")
                    .append("\"severity\": \"").append(json(severity(finding))).append("\", ")
                    .append("\"confidence\": \"").append(json(confidence(finding))).append("\", ")
                    .append("\"type\": \"").append(json(finding.type().label())).append("\", ")
                    .append("\"url\": \"").append(json(finding.url())).append("\", ")
                    .append("\"parameter\": \"").append(json(finding.parameter())).append("\", ")
                    .append("\"payload\": \"").append(json(finding.payload())).append("\", ")
                    .append("\"statusCode\": ").append(finding.statusCode()).append(", ")
                    .append("\"elapsedMs\": ").append(finding.elapsedMs()).append(", ")
                    .append("\"details\": \"").append(json(finding.details())).append("\", ")
                    .append("\"remediation\": \"").append(json(remediation(finding))).append("\", ")
                    .append("\"timestamp\": \"").append(json(finding.timestamp().toString())).append("\"")
                    .append("}");
        }
        json.append("\n  ]\n}\n");
        Files.writeString(path, json.toString(), StandardCharsets.UTF_8);
    }

    private static String json(String value) {
        String safe = value == null ? "" : value;
        return safe.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private static String link(String url) {
        String safe = url == null ? "" : url;
        String lower = safe.toLowerCase();
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
            return escape(safe);
        }
        return "<a href=\"" + escape(safe) + "\" target=\"_blank\" rel=\"noopener noreferrer\">"
                + escape(safe) + "</a>";
    }

    private static String csv(String value) {
        String safe = value == null ? "" : value;
        return "\"" + safe.replace("\"", "\"\"") + "\"";
    }

    private static String escape(String value) {
        return (value == null ? "" : value)
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
