package com.loxs.core;

import com.loxs.model.ScanType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UrlInjectorTest {
    @Test
    void normalizesUrlsWithoutScheme() {
        assertEquals("http://example.com/path", UrlInjector.normalize("example.com/path"));
        assertEquals("https://example.com", UrlInjector.normalize("https://example.com"));
        assertEquals("http://example.com", UrlInjector.normalize("//example.com"));
        assertEquals("https://example.com", UrlInjector.normalize("\uFEFFhttps://example.com"));
    }

    @Test
    void replacesOnlySelectedParameterAndPreservesOthers() {
        String result = UrlInjector.replaceParameter(
                "https://example.com/view?file=index&lang=tr#top", "file", "../../etc/passwd");
        assertTrue(result.startsWith("https://example.com/view?file=..%2F..%2Fetc%2Fpasswd&lang=tr#top"));
        assertTrue(result.contains("lang=tr"));
        assertTrue(result.endsWith("#top"));
    }

    @Test
    void addsParameterWhenMissing() {
        String result = UrlInjector.ensureParameter("https://example.com/page", "q");
        assertEquals("https://example.com/page?q=", result);
        assertEquals(List.of("q"), UrlInjector.parameterNames(result));
    }

    @Test
    void rawReplacementKeepsPercentEncoding() {
        String result = UrlInjector.replaceParameterRaw(
                "https://example.com/?next=local", "next", "/%0d%0aX-Loxs-Probe:abc");
        assertTrue(result.contains("%0d%0aX-Loxs-Probe:abc"));
        assertTrue(!result.contains("%250d%250a"));
    }

    @Test
    void appendsPayloadToPathBeforeQuery() {
        String result = UrlInjector.appendToPath("https://example.com/base?x=1", "/../../etc/passwd");
        assertEquals("https://example.com/base/../../etc/passwd?x=1", result);
    }

    @Test
    void extractsExpectedRedirectHost() {
        assertEquals("loxs-redirect.example", UrlInjector.expectedHost("https://loxs-redirect.example/"));
        assertEquals("example.com", UrlInjector.expectedHost("//example.com/path"));
        assertEquals(null, UrlInjector.expectedHost("/relative"));
    }
}
