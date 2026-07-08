package com.mtgtwitch.extension.desktop;

import com.mtgtwitch.extension.api.StatusController;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.OptionalInt;

public final class BridgeInstanceGuard {

    private static final Duration IDENTIFY_TIMEOUT = Duration.ofMillis(300);
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(IDENTIFY_TIMEOUT)
            .build();

    private BridgeInstanceGuard() {
    }

    public static boolean canBindServerPort(int port) {
        try (ServerSocket socket = new ServerSocket()) {
            socket.setReuseAddress(false);
            socket.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), port));
            return true;
        } catch (IOException exception) {
            return false;
        }
    }

    public static boolean isBridgeOnPort(int port) {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:%d/api/status".formatted(port)))
                .timeout(IDENTIFY_TIMEOUT)
                .GET()
                .build();

        try {
            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() >= 200
                    && response.statusCode() < 300
                    && response.body() != null
                    && response.body().contains("\"app\":\"" + StatusController.APP_ID + "\"");
        } catch (IOException | InterruptedException | IllegalArgumentException exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return false;
        }
    }

    public static ScanResult scanPorts(int minPort, int maxPort) {
        for (int port = minPort; port <= maxPort; port++) {
            if (canBindServerPort(port)) {
                return ScanResult.available(port);
            }

            if (isBridgeOnPort(port)) {
                return ScanResult.bridgeRunning(port);
            }
        }

        return ScanResult.noneAvailable();
    }

    public record ScanResult(ScanStatus status, OptionalInt port) {
        public static ScanResult available(int port) {
            return new ScanResult(ScanStatus.AVAILABLE, OptionalInt.of(port));
        }

        public static ScanResult bridgeRunning(int port) {
            return new ScanResult(ScanStatus.BRIDGE_RUNNING, OptionalInt.of(port));
        }

        public static ScanResult noneAvailable() {
            return new ScanResult(ScanStatus.NONE_AVAILABLE, OptionalInt.empty());
        }
    }

    public enum ScanStatus {
        AVAILABLE,
        BRIDGE_RUNNING,
        NONE_AVAILABLE
    }
}
