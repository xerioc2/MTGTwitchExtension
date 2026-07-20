package com.mtgtwitch.extension.desktop;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;

public final class BridgeLocalConfigStore {

    public static final Path CONFIG_PATH = Path.of(
            System.getenv("APPDATA") == null ? System.getProperty("user.home") : System.getenv("APPDATA"),
            "MTGO Twitch Bridge",
            "config.properties"
    );
    public static final String MTGO_USERNAMES_KEY = "mtgo.usernames";

    private BridgeLocalConfigStore() {
    }

    public static Properties loadProperties(Path configPath) throws IOException {
        Properties properties = new Properties();
        if (!Files.exists(configPath)) {
            return properties;
        }

        try (java.io.Reader reader = Files.newBufferedReader(configPath, StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
        return properties;
    }

    public static void saveProperties(Path configPath, Properties properties) throws IOException {
        Files.createDirectories(configPath.getParent());
        try (java.io.Writer writer = Files.newBufferedWriter(configPath, StandardCharsets.UTF_8)) {
            properties.store(writer, "MTGO Twitch Bridge");
        }
    }

    public static List<String> readMtgoUsernames(Path configPath) {
        try {
            return parseUsernames(loadProperties(configPath).getProperty(MTGO_USERNAMES_KEY, ""));
        } catch (IOException exception) {
            return List.of();
        }
    }

    public static void writeMtgoUsernames(Path configPath, Collection<String> usernames) throws IOException {
        Properties properties = loadProperties(configPath);
        List<String> normalizedUsernames = normalizeUsernames(usernames);
        if (normalizedUsernames.isEmpty()) {
            properties.remove(MTGO_USERNAMES_KEY);
        } else {
            properties.setProperty(MTGO_USERNAMES_KEY, serializeUsernames(normalizedUsernames));
        }
        saveProperties(configPath, properties);
    }

    public static List<String> parseUsernames(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }

        return normalizeUsernames(List.of(value.split(",")));
    }

    public static String serializeUsernames(Collection<String> usernames) {
        return String.join(",", normalizeUsernames(usernames));
    }

    public static List<String> normalizeUsernames(Collection<String> usernames) {
        if (usernames == null || usernames.isEmpty()) {
            return List.of();
        }

        Set<String> seen = new LinkedHashSet<>();
        List<String> normalized = new ArrayList<>();
        for (String username : usernames) {
            if (username == null) {
                continue;
            }

            String trimmed = username.trim();
            if (trimmed.isEmpty()) {
                continue;
            }

            String key = trimmed.toLowerCase(Locale.ROOT);
            if (seen.add(key)) {
                normalized.add(trimmed);
            }
        }

        return List.copyOf(normalized);
    }
}
