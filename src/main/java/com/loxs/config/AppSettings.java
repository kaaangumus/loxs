package com.loxs.config;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public final class AppSettings {
    private final Path file;
    private final Properties properties;

    private AppSettings(Path file, Properties properties) {
        this.file = file;
        this.properties = properties;
    }

    public static AppSettings load() {
        Path directory;
        String appData = System.getenv("APPDATA");
        if (appData != null && !appData.isBlank()) {
            directory = Path.of(appData, "LOXS");
        } else {
            directory = Path.of(System.getProperty("user.home"), ".loxs");
        }
        Path file = directory.resolve("settings.properties");
        Properties properties = new Properties();
        if (Files.isRegularFile(file)) {
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                properties.load(reader);
            } catch (IOException ignored) {
                // Bozuk ayar dosyası varsayılanlarla devam eder.
            }
        }
        return new AppSettings(file, properties);
    }

    public String get(String key, String fallback) {
        String value = properties.getProperty(key);
        return value == null || value.isBlank() ? fallback : value;
    }

    public int getInt(String key, int fallback) {
        try {
            return Integer.parseInt(get(key, String.valueOf(fallback)));
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    public double getDouble(String key, double fallback) {
        try {
            return Double.parseDouble(get(key, String.valueOf(fallback)));
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    public boolean getBoolean(String key, boolean fallback) {
        return Boolean.parseBoolean(get(key, String.valueOf(fallback)));
    }

    public void set(String key, String value) {
        properties.setProperty(key, value == null ? "" : value);
    }

    public void setInt(String key, int value) {
        set(key, String.valueOf(value));
    }

    public void setDouble(String key, double value) {
        set(key, String.valueOf(value));
    }

    public void setBoolean(String key, boolean value) {
        set(key, String.valueOf(value));
    }

    public void save() {
        try {
            Files.createDirectories(file.getParent());
            try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                properties.store(writer, "LOXS settings (cookie is not stored)");
            }
        } catch (IOException ignored) {
            // Ayar kaydedilemezse uygulama çalışmaya devam eder.
        }
    }
}
