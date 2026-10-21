package com.loxs.model;

import java.time.Instant;

public record Finding(
        ScanType type,
        boolean vulnerable,
        String url,
        String parameter,
        String payload,
        int statusCode,
        long elapsedMs,
        String details,
        Instant timestamp
) {
    public Finding {
        url = url == null ? "" : url;
        parameter = parameter == null ? "" : parameter;
        payload = payload == null ? "" : payload;
        details = details == null ? "" : details;
        timestamp = timestamp == null ? Instant.now() : timestamp;
    }

    public String statusText() {
        return vulnerable ? "VULNERABLE" : "Temiz";
    }
}
