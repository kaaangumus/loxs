package com.loxs.model;

import java.time.Duration;
import java.util.List;

public record ScanRequest(
        ScanType type,
        List<String> targets,
        List<String> payloads,
        String cookie,
        int threads,
        Duration timeout,
        List<String> criteria,
        double delayThresholdSeconds,
        double baselineMarginSeconds,
        boolean verifyTls
) {
    public ScanRequest {
        if (type == null) {
            throw new IllegalArgumentException("Tarayıcı türü boş olamaz.");
        }
        if (targets == null || targets.isEmpty()) {
            throw new IllegalArgumentException("En az bir hedef URL gerekli.");
        }
        if (threads < 1 || threads > 20) {
            throw new IllegalArgumentException("Thread sayısı 1-20 arasında olmalı.");
        }
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("Zaman aşımı sıfırdan büyük olmalı.");
        }
        if (delayThresholdSeconds <= 0) {
            throw new IllegalArgumentException("SQLi eşiği sıfırdan büyük olmalı.");
        }
        if (baselineMarginSeconds < 0) {
            throw new IllegalArgumentException("Taban farkı negatif olamaz.");
        }
        targets = List.copyOf(targets);
        payloads = payloads == null ? List.of() : List.copyOf(payloads);
        criteria = criteria == null ? List.of() : List.copyOf(criteria);
        cookie = cookie == null || cookie.isBlank() ? null : cookie.strip();
    }
}
