package com.loxs.core;

import com.loxs.model.Finding;
import com.loxs.model.ScanRequest;
import com.loxs.model.ScanType;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

final class TestHttpServer implements AutoCloseable {
    private final HttpServer server;
    private final int port;

    TestHttpServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(runnable, "test-http");
            thread.setDaemon(true);
            return thread;
        }));
        server.createContext("/sqli", exchange -> {
            String query = decodedQuery(exchange);
            if (query.toLowerCase().contains("sleep") || query.toLowerCase().contains("pg_sleep")
                    || query.toLowerCase().contains("waitfor")) {
                sleep(350);
            } else {
                sleep(10);
            }
            respond(exchange, 200, "ok", null);
        });
        server.createContext("/sqli-timeout", exchange -> {
            sleep(2_000);
            respond(exchange, 200, "slow", null);
        });
        server.createContext("/lfi", exchange -> {
            String query = decodedQuery(exchange);
            if (query.contains("passwd") || query.contains("win.ini")) {
                respond(exchange, 200, "root:x:0:0:root:/root:/bin/bash\n[boot loader]", null);
            } else {
                respond(exchange, 200, "<html><body>normal page</body></html>", null);
            }
        });
        server.createContext("/crlf", exchange -> {
            String raw = exchange.getRequestURI().toString();
            if (raw.contains("X-Loxs-Probe")) {
                int index = raw.indexOf("X-Loxs-Probe:");
                respond(exchange, 200, "ok", "X-Loxs-Probe: " + raw.substring(index));
            } else {
                respond(exchange, 200, "ok", null);
            }
        });
        server.createContext("/xss", exchange -> {
            String query = decodedQuery(exchange);
            respond(exchange, 200, "<html><body>" + query + "</body></html>", null);
        });
        server.createContext("/xss-safe", exchange -> {
            String query = decodedQuery(exchange);
            String escaped = query.replace("<", "&lt;").replace(">", "&gt;");
            respond(exchange, 200, "<html><body>" + escaped + "</body></html>", null);
        });
        server.createContext("/redirect", exchange -> {
            String next = "";
            for (String part : decodedQuery(exchange).split("&")) {
                if (part.startsWith("next=")) {
                    next = part.substring("next=".length());
                }
            }
            if (next.startsWith("http://") || next.startsWith("https://") || next.startsWith("//")) {
                exchange.getResponseHeaders().set("Location", next);
                exchange.sendResponseHeaders(302, -1);
                exchange.close();
            } else {
                respond(exchange, 200, "ok", null);
            }
        });
        server.createContext("/redirect-same", exchange -> {
            exchange.getResponseHeaders().set("Location", "http://127.0.0.1:"
                    + server.getAddress().getPort() + "/landing");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        server.createContext("/slow", exchange -> {
            sleep(5_000);
            respond(exchange, 200, "slow", null);
        });
        server.start();
        port = server.getAddress().getPort();
    }

    private static String decodedQuery(HttpExchange exchange) {
        String rawQuery = exchange.getRequestURI().getRawQuery();
        return rawQuery == null ? "" : URLDecoder.decode(rawQuery, StandardCharsets.UTF_8);
    }

    private static void respond(HttpExchange exchange, int status, String body, String headerNameAndValue)
            throws IOException {
        if (headerNameAndValue != null) {
            String[] parts = headerNameAndValue.split(":", 2);
            exchange.getResponseHeaders().set(parts[0], parts[1].strip());
        }
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    int port() {
        return port;
    }

    String url(String path) {
        return "http://127.0.0.1:" + port + path;
    }

    @Override
    public void close() {
        server.stop(0);
    }

    static final class Collector implements ScanListener {
        final List<Finding> findings = Collections.synchronizedList(new ArrayList<>());
        final List<String> logs = Collections.synchronizedList(new ArrayList<>());
        final AtomicBoolean cancelled = new AtomicBoolean();

        @Override
        public void log(String message) {
            logs.add(message);
        }

        @Override
        public void finding(Finding finding) {
            findings.add(finding);
        }

        @Override
        public void progress(int completed, int total) {
        }

        @Override
        public boolean cancelled() {
            return cancelled.get();
        }

        List<Finding> vulnerable() {
            synchronized (findings) {
                return findings.stream().filter(Finding::vulnerable).toList();
            }
        }
    }

    static ScanRequest request(ScanType type, String target, List<String> payloads, List<String> criteria,
                               Duration timeout, double threshold, double margin) {
        return new ScanRequest(type, List.of(target), payloads, null, 2, timeout,
                criteria, threshold, margin, true);
    }
}
