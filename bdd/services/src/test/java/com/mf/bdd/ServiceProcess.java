package com.mf.bdd;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * A service under test. If it already answers its health endpoint (for example when FUND_DATA_URL or
 * PARSER_URL points at a running instance) it is used as is; otherwise it is started from the built
 * artifacts and stopped again by {@link #stopAll()}.
 */
final class ServiceProcess {

    private static final Path ROOT = Path.of(System.getProperty("user.dir")).resolve("../..").normalize();
    private static final Duration STARTUP_TIMEOUT = Duration.ofSeconds(90);
    private static final HttpClient HTTP = HttpClient.newHttpClient();

    private static final Map<String, ServiceProcess> SERVICES = Map.of(
            "fund-data",
            new ServiceProcess(
                    "fund-data",
                    envOrDefault("FUND_DATA_URL", "http://localhost:8081"),
                    "/actuator/health",
                    ROOT.resolve("services/fund-data"),
                    List.of(
                            Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                            "-jar",
                            ROOT.resolve("services/fund-data/target/fund-data-0.1.0-SNAPSHOT.jar").toString())),
            "statement-parser",
            new ServiceProcess(
                    "statement-parser",
                    envOrDefault("PARSER_URL", "http://localhost:8082"),
                    "/health",
                    ROOT.resolve("services/statement-parser"),
                    List.of("uv", "run", "uvicorn", "statement_parser.main:app", "--port", "8082")));

    private final String name;
    private final String baseUrl;
    private final String healthPath;
    private final Path workDir;
    private final List<String> command;
    private Process process;

    private ServiceProcess(String name, String baseUrl, String healthPath, Path workDir, List<String> command) {
        this.name = name;
        this.baseUrl = baseUrl;
        this.healthPath = healthPath;
        this.workDir = workDir;
        this.command = command;
    }

    static ServiceProcess named(String name) {
        ServiceProcess service = SERVICES.get(name);
        if (service == null) {
            throw new IllegalArgumentException("Unknown service: " + name);
        }
        return service;
    }

    static void stopAll() {
        SERVICES.values().forEach(ServiceProcess::stop);
    }

    URI uri(String path) {
        return URI.create(baseUrl + path);
    }

    synchronized void ensureRunning() throws Exception {
        if (isHealthy()) {
            return;
        }
        Path log = ROOT.resolve("bdd/services/target/" + name + ".log");
        Files.createDirectories(log.getParent());
        process = new ProcessBuilder(command)
                .directory(workDir.toFile())
                .redirectErrorStream(true)
                .redirectOutput(log.toFile())
                .start();

        long deadline = System.nanoTime() + STARTUP_TIMEOUT.toNanos();
        while (System.nanoTime() < deadline) {
            if (!process.isAlive()) {
                throw new IllegalStateException(name + " exited with " + process.exitValue() + "; see " + log);
            }
            if (isHealthy()) {
                return;
            }
            Thread.sleep(500);
        }
        throw new IllegalStateException(name + " did not become healthy within " + STARTUP_TIMEOUT + "; see " + log);
    }

    private boolean isHealthy() throws InterruptedException {
        try {
            var request = HttpRequest.newBuilder(uri(healthPath)).GET().build();
            return HTTP.send(request, HttpResponse.BodyHandlers.discarding()).statusCode() == 200;
        } catch (IOException notUpYet) {
            return false;
        }
    }

    private synchronized void stop() {
        if (process != null) {
            process.descendants().forEach(ProcessHandle::destroy);
            process.destroy();
            process = null;
        }
    }

    private static String envOrDefault(String variable, String fallback) {
        String value = System.getenv(variable);
        return value == null || value.isBlank() ? fallback : value;
    }
}
