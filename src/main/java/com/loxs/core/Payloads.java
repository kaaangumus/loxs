package com.loxs.core;

import com.loxs.model.ScanType;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class Payloads {
    public static final int MAX_PAYLOADS = 100_000;
    public static final int MAX_PAYLOAD_LENGTH = 8_192;

    private static final Map<ScanType, List<String>> CACHE = new EnumMap<>(ScanType.class);

    private Payloads() {
    }

    public static List<String> defaults(ScanType type) {
        return CACHE.computeIfAbsent(type, Payloads::loadDefault);
    }

    private static List<String> loadDefault(ScanType type) {
        String resource = type.payloadResource();
        if (resource == null) {
            return List.of();
        }
        try (InputStream input = Payloads.class.getResourceAsStream("/payloads/" + resource)) {
            if (input == null) {
                throw new IllegalStateException("Varsayılan payload dosyası bulunamadı: " + resource);
            }
            return parse(new String(input.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException exception) {
            throw new IllegalStateException("Payload dosyası okunamadı: " + resource, exception);
        }
    }

    public static List<String> loadFile(Path path) throws IOException {
        if (path == null || !Files.isRegularFile(path)) {
            throw new IOException("Payload dosyası bulunamadı: " + path);
        }
        return parse(Files.readString(path, StandardCharsets.UTF_8));
    }

    private static List<String> parse(String content) {
        List<String> payloads = new ArrayList<>();
        for (String line : content.split("\\R")) {
            String value = line.replace("\uFEFF", "").strip();
            if (value.isEmpty() || value.startsWith("#")) {
                continue;
            }
            if (value.length() > MAX_PAYLOAD_LENGTH) {
                continue;
            }
            payloads.add(value);
            if (payloads.size() >= MAX_PAYLOADS) {
                break;
            }
        }
        return List.copyOf(payloads);
    }

    public static List<String> require(ScanType type) {
        List<String> payloads = defaults(type);
        if (payloads.isEmpty()) {
            throw new IllegalStateException(type.label() + " için payload listesi boş.");
        }
        return payloads;
    }
}
