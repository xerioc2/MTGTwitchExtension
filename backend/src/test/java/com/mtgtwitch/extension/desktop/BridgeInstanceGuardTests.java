package com.mtgtwitch.extension.desktop;

import com.mtgtwitch.extension.api.StatusController;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class BridgeInstanceGuardTests {

    @Test
    void detectsWhenConfiguredServerPortIsAlreadyBound() throws Exception {
        try (ServerSocket socket = new ServerSocket()) {
            socket.setReuseAddress(false);
            socket.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));

            assertThat(BridgeInstanceGuard.canBindServerPort(socket.getLocalPort())).isFalse();
        }
    }

    @Test
    void reportsAvailablePortWhenNothingIsBound() throws Exception {
        int freePort;
        try (ServerSocket socket = new ServerSocket()) {
            socket.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
            freePort = socket.getLocalPort();
        }

        assertThat(BridgeInstanceGuard.canBindServerPort(freePort)).isTrue();
    }

    @Test
    void identifiesBridgeStatusJsonOnOccupiedPort() throws Exception {
        HttpServer server = statusServer("{\"app\":\"%s\",\"port\":8080}".formatted(StatusController.APP_ID));
        try {
            assertThat(BridgeInstanceGuard.isBridgeOnPort(server.getAddress().getPort())).isTrue();
        } finally {
            server.stop(0);
        }
    }

    @Test
    void doesNotIdentifyOtherJsonOrGarbageAsBridge() throws Exception {
        HttpServer otherJsonServer = statusServer("{\"app\":\"something-else\",\"port\":8080}");
        try {
            assertThat(BridgeInstanceGuard.isBridgeOnPort(otherJsonServer.getAddress().getPort())).isFalse();
        } finally {
            otherJsonServer.stop(0);
        }

        HttpServer garbageServer = statusServer("not json");
        try {
            assertThat(BridgeInstanceGuard.isBridgeOnPort(garbageServer.getAddress().getPort())).isFalse();
        } finally {
            garbageServer.stop(0);
        }
    }

    @Test
    void connectionRefusedIsNotTreatedAsBridge() throws Exception {
        int freePort;
        try (ServerSocket socket = new ServerSocket()) {
            socket.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
            freePort = socket.getLocalPort();
        }

        assertThat(BridgeInstanceGuard.isBridgeOnPort(freePort)).isFalse();
    }

    @Test
    void scanRollsPastNonBridgeOccupantToNextAvailablePort() throws Exception {
        int occupiedPort = freePortWithFreeSuccessor();
        HttpServer nonBridgeServer = statusServer(occupiedPort, "{\"app\":\"dev-server\"}");
        try {
            BridgeInstanceGuard.ScanResult result = BridgeInstanceGuard.scanPorts(occupiedPort, occupiedPort + 1);

            assertThat(result.status()).isEqualTo(BridgeInstanceGuard.ScanStatus.AVAILABLE);
            assertThat(result.port()).hasValue(occupiedPort + 1);
        } finally {
            nonBridgeServer.stop(0);
        }
    }

    private HttpServer statusServer(String body) throws Exception {
        return statusServer(0, body);
    }

    private HttpServer statusServer(int port, String body) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), 0);
        server.createContext("/api/status", exchange -> {
            byte[] response = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        return server;
    }

    private int freePortWithFreeSuccessor() throws Exception {
        for (int attempts = 0; attempts < 20; attempts++) {
            int port;
            try (ServerSocket socket = new ServerSocket()) {
                socket.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
                port = socket.getLocalPort();
            }

            if (port < 65535 && BridgeInstanceGuard.canBindServerPort(port + 1)) {
                return port;
            }
        }

        throw new IllegalStateException("Could not find consecutive free ports for test.");
    }
}
