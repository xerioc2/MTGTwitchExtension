package com.mtgtwitch.extension.api;

import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class StatusController {

    private static final int DEFAULT_PORT = 8080;

    private final Environment environment;

    public StatusController(Environment environment) {
        this.environment = environment;
    }

    @GetMapping("/status")
    public BridgeStatus status() {
        String localPort = environment.getProperty("local.server.port");
        String configuredPort = environment.getProperty("server.port");
        return new BridgeStatus(parsePort(localPort, parsePort(configuredPort, DEFAULT_PORT)));
    }

    private int parsePort(String value, int fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }

        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    public record BridgeStatus(int port) {
    }
}
