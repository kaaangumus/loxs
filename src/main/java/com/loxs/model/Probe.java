package com.loxs.model;

public record Probe(
        ScanType type,
        String url,
        String parameter,
        String payload,
        String target
) {
}
