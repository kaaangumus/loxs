package com.loxs.report;

import com.loxs.model.Finding;
import com.loxs.model.ScanType;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReportWriterTest {
    @Test
    void escapesHtmlFromUrlPayloadAndDetails() throws Exception {
        Path report = Files.createTempFile("loxs-report-", ".html");
        try {
            Finding finding = new Finding(
                    ScanType.XSS,
                    true,
                    "https://example.com/?q=<script>alert(1)</script>",
                    "q",
                    "<script>alert(1)</script>",
                    200,
                    12,
                    "Detay <img src=x onerror=alert(1)>",
                    Instant.now());
            ReportWriter.writeHtml(report, ScanType.XSS, 1, List.of(finding), 1);
            String html = Files.readString(report);
            assertTrue(html.contains("&lt;script&gt;alert(1)&lt;/script&gt;"));
            assertFalse(html.contains("<script>alert(1)</script>"));
        } finally {
            Files.deleteIfExists(report);
        }
    }

    @Test
    void writesJsonWithSeverityAndRemediation() throws Exception {
        Path report = Files.createTempFile("loxs-report-", ".json");
        try {
            Finding finding = new Finding(ScanType.SQLI, true,
                    "https://example.com/?id=1", "id", "' OR 1=1--", 200, 30,
                    "Zaman tabanlı SQLi doğrulandı.", Instant.now());
            ReportWriter.writeJson(report, "SQLi", 1, List.of(finding), 1);
            String json = Files.readString(report);
            assertTrue(json.contains("\"tool\": \"LOXS\""));
            assertTrue(json.contains("\"severity\": \"Yüksek\""));
            assertTrue(json.contains("\"remediation\""));
        } finally {
            Files.deleteIfExists(report);
        }
    }

    @Test
    void writesCsvWithQuotedValues() throws Exception {
        Path report = Files.createTempFile("loxs-report-", ".csv");
        try {
            Finding finding = new Finding(ScanType.LFI, true, "https://example.com/?file=a,b",
                    "file", "../../etc/passwd", 200, 20, "test", Instant.now());
            ReportWriter.writeCsv(report, List.of(finding));
            String csv = Files.readString(report);
            assertTrue(csv.contains("\"https://example.com/?file=a,b\""));
            assertTrue(csv.contains("VULNERABLE"));
        } finally {
            Files.deleteIfExists(report);
        }
    }
}
