package com.loxs.core;

import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class UrlInjector {
    private UrlInjector() {
    }

    public static String normalize(String rawUrl) {
        if (rawUrl == null) {
            return "";
        }
        String value = rawUrl.replace("\uFEFF", "").strip();
        if (value.isEmpty()) {
            return value;
        }
        if (value.startsWith("//")) {
            return "http:" + value;
        }
        if (value.matches("(?i)^[a-z][a-z0-9+.-]*://.*")) {
            return value;
        }
        return "http://" + value;
    }

    public static String ensureParameter(String url, String parameter) {
        if (!parameterNames(url).isEmpty()) {
            return url;
        }
        return replaceParameter(url, parameter, "");
    }

    public static List<String> parameterNames(String url) {
        ParsedUrl parsed = ParsedUrl.parse(url);
        List<String> names = new ArrayList<>();
        for (Pair pair : parseQuery(parsed.query())) {
            if (!pair.raw().isEmpty() && !names.contains(pair.decodedName())) {
                names.add(pair.decodedName());
            }
        }
        return names;
    }

    public static String originalValue(String url, String parameter) {
        ParsedUrl parsed = ParsedUrl.parse(url);
        for (Pair pair : parseQuery(parsed.query())) {
            if (pair.decodedName().equals(parameter)) {
                return pair.decodedValue();
            }
        }
        return "1";
    }

    public static String replaceParameter(String url, String parameter, String value) {
        ParsedUrl parsed = ParsedUrl.parse(url);
        List<Pair> pairs = parseQuery(parsed.query());
        boolean replaced = false;
        List<String> parts = new ArrayList<>();
        for (Pair pair : pairs) {
            if (pair.decodedName().equals(parameter)) {
                parts.add(encode(parameter) + "=" + encode(value));
                replaced = true;
            } else {
                parts.add(pair.raw());
            }
        }
        if (!replaced) {
            parts.add(encode(parameter) + "=" + encode(value));
        }
        return parsed.base() + "?" + String.join("&", parts) + parsed.fragment();
    }

    /**
     * CRLF payload'ları zaten yüzde-kodlu olduğu için tekrar encode edilmez.
     * Sadece boşluklar URI için güvenli hale getirilir.
     */
    public static String replaceParameterRaw(String url, String parameter, String rawValue) {
        ParsedUrl parsed = ParsedUrl.parse(url);
        List<Pair> pairs = parseQuery(parsed.query());
        boolean replaced = false;
        List<String> parts = new ArrayList<>();
        for (Pair pair : pairs) {
            if (pair.decodedName().equals(parameter)) {
                parts.add(encode(parameter) + "=" + safeRawValue(rawValue));
                replaced = true;
            } else {
                parts.add(pair.raw());
            }
        }
        if (!replaced) {
            parts.add(encode(parameter) + "=" + safeRawValue(rawValue));
        }
        return parsed.base() + "?" + String.join("&", parts) + parsed.fragment();
    }

    private static String safeRawValue(String rawValue) {
        return (rawValue == null ? "" : rawValue)
                .replace(" ", "%20")
                .replace("#", "%23");
    }

    public static String appendToPath(String url, String payload) {
        ParsedUrl parsed = ParsedUrl.parse(url);
        String path = parsed.base();
        if (payload.startsWith("/") && path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        return path + payload + (parsed.query().isEmpty() ? "" : "?" + parsed.query()) + parsed.fragment();
    }

    public static String host(String url) {
        try {
            URI uri = URI.create(normalize(url));
            return uri.getHost() == null ? "" : uri.getHost();
        } catch (IllegalArgumentException exception) {
            return "";
        }
    }

    public static String expectedHost(String payload) {
        String value = payload.strip();
        if (value.isEmpty()) {
            return null;
        }
        try {
            if (value.startsWith("//")) {
                value = "http:" + value;
            } else if (!value.matches("(?i)^[a-z][a-z0-9+.-]*://.*")) {
                return null;
            }
            URI uri = URI.create(value);
            return uri.getHost();
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    public static boolean sameHost(String first, String second) {
        if (first == null || second == null) {
            return false;
        }
        String a = first.toLowerCase();
        String b = second.toLowerCase();
        if (a.equals(b)) {
            return true;
        }
        return a.equals("www." + b) || b.equals("www." + a);
    }

    public static String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8)
                .replace("+", "%20");
    }

    public static String decode(String value) {
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException exception) {
            return value;
        }
    }

    private static List<Pair> parseQuery(String query) {
        List<Pair> pairs = new ArrayList<>();
        if (query == null || query.isEmpty()) {
            return pairs;
        }
        for (String rawPart : query.split("&", -1)) {
            if (rawPart.isEmpty()) {
                continue;
            }
            int equals = rawPart.indexOf('=');
            String rawName = equals >= 0 ? rawPart.substring(0, equals) : rawPart;
            String rawValue = equals >= 0 ? rawPart.substring(equals + 1) : "";
            pairs.add(new Pair(rawPart, decode(rawName), decode(rawValue)));
        }
        return pairs;
    }

    private record Pair(String raw, String decodedName, String decodedValue) {
    }

    private record ParsedUrl(String base, String query, String fragment) {
        static ParsedUrl parse(String url) {
            String value = url == null ? "" : url;
            String fragment = "";
            int hash = value.indexOf('#');
            if (hash >= 0) {
                fragment = value.substring(hash);
                value = value.substring(0, hash);
            }
            int question = value.indexOf('?');
            String base = question >= 0 ? value.substring(0, question) : value;
            String query = question >= 0 ? value.substring(question + 1) : "";
            return new ParsedUrl(base, query, fragment);
        }
    }
}
