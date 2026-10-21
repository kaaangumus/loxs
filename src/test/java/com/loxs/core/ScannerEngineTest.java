package com.loxs.core;

import com.loxs.model.ScanRequest;
import com.loxs.model.ScanType;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScannerEngineTest {
    @Test
    void detectsConfirmedTimeBasedSqli() throws Exception {
        try (TestHttpServer server = new TestHttpServer()) {
            TestHttpServer.Collector collector = new TestHttpServer.Collector();
            ScanRequest request = TestHttpServer.request(
                    ScanType.SQLI,
                    server.url("/sqli?id=1"),
                    List.of(" AND SLEEP(0.4)-- -"),
                    List.of(),
                    Duration.ofSeconds(3),
                    0.2,
                    0.1);

            new ScannerEngine().scan(request, collector);

            assertFalse(collector.vulnerable().isEmpty(), "SQLi bulgusu bekleniyordu");
            assertTrue(collector.vulnerable().get(0).details().contains("doğrulandı"));
        }
    }

    @Test
    void doesNotReportTimeoutAsSqli() throws Exception {
        try (TestHttpServer server = new TestHttpServer()) {
            TestHttpServer.Collector collector = new TestHttpServer.Collector();
            ScanRequest request = TestHttpServer.request(
                    ScanType.SQLI,
                    server.url("/sqli-timeout?id=1"),
                    List.of(" AND SLEEP(0.4)-- -"),
                    List.of(),
                    Duration.ofMillis(400),
                    0.2,
                    0.1);

            new ScannerEngine().scan(request, collector);

            assertTrue(collector.vulnerable().isEmpty(), "Timeout SQLi olarak raporlanmamalı");
        }
    }

    @Test
    void detectsLfiWithSuccessCriteria() throws Exception {
        try (TestHttpServer server = new TestHttpServer()) {
            TestHttpServer.Collector collector = new TestHttpServer.Collector();
            ScanRequest request = TestHttpServer.request(
                    ScanType.LFI,
                    server.url("/lfi?file=index"),
                    List.of("../../etc/passwd"),
                    List.of("root:x:0:0:"),
                    Duration.ofSeconds(3),
                    1,
                    1);

            new ScannerEngine().scan(request, collector);

            assertFalse(collector.vulnerable().isEmpty(), "LFI bulgusu bekleniyordu");
        }
    }

    @Test
    void detectsReflectedXssOnlyWhenPayloadIsRaw() throws Exception {
        try (TestHttpServer server = new TestHttpServer()) {
            TestHttpServer.Collector vulnerableCollector = new TestHttpServer.Collector();
            new ScannerEngine().scan(TestHttpServer.request(
                    ScanType.XSS,
                    server.url("/xss?q=test"),
                    List.of("\"><script>alert('{MARKER}')</script>"),
                    List.of(),
                    Duration.ofSeconds(3), 1, 1), vulnerableCollector);
            assertFalse(vulnerableCollector.vulnerable().isEmpty(), "Yansıyan XSS bekleniyordu");

            TestHttpServer.Collector safeCollector = new TestHttpServer.Collector();
            new ScannerEngine().scan(TestHttpServer.request(
                    ScanType.XSS,
                    server.url("/xss-safe?q=test"),
                    List.of("\"><script>alert('{MARKER}')</script>"),
                    List.of(),
                    Duration.ofSeconds(3), 1, 1), safeCollector);
            assertTrue(safeCollector.vulnerable().isEmpty(), "Escape edilmiş yansıma XSS sayılmamalı");
        }
    }

    @Test
    void detectsCrlfHeaderInjection() throws Exception {
        try (TestHttpServer server = new TestHttpServer()) {
            TestHttpServer.Collector collector = new TestHttpServer.Collector();
            ScanRequest request = TestHttpServer.request(
                    ScanType.CRLF,
                    server.url("/crlf"),
                    List.of("/%0d%0aX-Loxs-Probe:{MARKER}"),
                    List.of(),
                    Duration.ofSeconds(3), 1, 1);

            new ScannerEngine().scan(request, collector);

            assertFalse(collector.vulnerable().isEmpty(), "CRLF bulgusu bekleniyordu");
        }
    }

    @Test
    void detectsOpenRedirectToPayloadHost() throws Exception {
        try (TestHttpServer server = new TestHttpServer()) {
            TestHttpServer.Collector collector = new TestHttpServer.Collector();
            ScanRequest request = TestHttpServer.request(
                    ScanType.OPEN_REDIRECT,
                    server.url("/redirect?next=local"),
                    List.of("https://loxs-redirect.example/"),
                    List.of(),
                    Duration.ofSeconds(3), 1, 1);

            new ScannerEngine().scan(request, collector);

            assertFalse(collector.vulnerable().isEmpty(), "Open redirect bulgusu bekleniyordu");
        }
    }

    @Test
    void doesNotReportSameHostRedirect() throws Exception {
        try (TestHttpServer server = new TestHttpServer()) {
            TestHttpServer.Collector collector = new TestHttpServer.Collector();
            ScanRequest request = TestHttpServer.request(
                    ScanType.OPEN_REDIRECT,
                    server.url("/redirect-same?next=local"),
                    List.of("https://loxs-redirect.example/"),
                    List.of(),
                    Duration.ofSeconds(3), 1, 1);

            new ScannerEngine().scan(request, collector);

            assertTrue(collector.vulnerable().isEmpty(), "Aynı host yönlendirmesi açık redirect sayılmamalı");
        }
    }

    @Test
    void cancellationStopsScanPromptly() throws Exception {
        try (TestHttpServer server = new TestHttpServer()) {
            TestHttpServer.Collector collector = new TestHttpServer.Collector();
            ScanRequest request = TestHttpServer.request(
                    ScanType.XSS,
                    server.url("/slow?q=test"),
                    List.of("<script>alert('{MARKER}')</script>"),
                    List.of(),
                    Duration.ofSeconds(10), 1, 1);

            ScannerEngine engine = new ScannerEngine();
            Thread thread = new Thread(() -> engine.scan(request, collector));
            thread.start();
            Thread.sleep(250);
            engine.cancel();
            thread.join(3_000);

            assertFalse(thread.isAlive(), "İptal sonrası tarama 3 saniye içinde bitmeliydi");
        }
    }
}
