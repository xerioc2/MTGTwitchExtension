package com.mtgtwitch.extension.desktop;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

public final class WindowsStartupRegistry {

    static final String STARTUP_PREFERENCE_KEY = "startup.autostart";
    static final String RUN_KEY = "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run";
    static final String RUN_VALUE_NAME = "MTGOTwitchBridge";

    private static final Logger log = LoggerFactory.getLogger(WindowsStartupRegistry.class);
    private static final int REG_TIMEOUT_SECONDS = 3;

    private WindowsStartupRegistry() {
    }

    public static boolean readPreference(Path configPath) {
        try {
            return Boolean.parseBoolean(
                    BridgeLocalConfigStore.loadProperties(configPath)
                            .getProperty(STARTUP_PREFERENCE_KEY, "false")
            );
        } catch (IOException exception) {
            log.warn("Could not read bridge autostart preference.", exception);
            return false;
        }
    }

    public static void reconcileStartupPreference(Path configPath) {
        if (!readPreference(configPath)) {
            return;
        }

        syncRegistryToPreference(true);
    }

    public static void setPreference(Path configPath, boolean enabled) {
        try {
            Properties properties = BridgeLocalConfigStore.loadProperties(configPath);
            properties.setProperty(STARTUP_PREFERENCE_KEY, Boolean.toString(enabled));
            BridgeLocalConfigStore.saveProperties(configPath, properties);
        } catch (IOException exception) {
            log.warn("Could not save bridge autostart preference.", exception);
        }

        syncRegistryToPreference(enabled);
    }

    private static void syncRegistryToPreference(boolean enabled) {
        if (!isWindows()) {
            log.warn("Bridge autostart is only supported on Windows.");
            return;
        }

        Optional<String> executablePath = currentExecutablePath();
        if (executablePath.isEmpty()) {
            log.warn("Could not resolve current bridge executable path for autostart.");
            return;
        }

        Optional<String> registryValue = queryRunValue();
        RegistryAction action = decideRegistryAction(enabled, registryValue.orElse(null), executablePath.get());
        try {
            switch (action) {
                case WRITE -> writeRunValue(executablePath.get());
                case DELETE -> deleteRunValue();
                case LEAVE -> {
                }
            }
        } catch (IOException exception) {
            log.warn("Could not update Windows bridge autostart registry value.", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted while updating Windows bridge autostart registry value.", exception);
        }
    }

    static RegistryAction decideRegistryAction(boolean preferenceEnabled, String currentRegistryValue, String executablePath) {
        if (!preferenceEnabled) {
            return isBlank(currentRegistryValue) ? RegistryAction.LEAVE : RegistryAction.DELETE;
        }

        if (isBlank(currentRegistryValue)) {
            return RegistryAction.WRITE;
        }

        String currentPath = normalizeRunValue(currentRegistryValue);
        String nextPath = normalizeRunValue(executablePath);
        return currentPath.equalsIgnoreCase(nextPath) ? RegistryAction.LEAVE : RegistryAction.WRITE;
    }

    static List<String> regAddCommand(String executablePath) {
        return List.of(
                "reg.exe",
                "add",
                RUN_KEY,
                "/v",
                RUN_VALUE_NAME,
                "/t",
                "REG_SZ",
                "/d",
                quoteRunValue(executablePath),
                "/f"
        );
    }

    static List<String> regQueryCommand() {
        return List.of("reg.exe", "query", RUN_KEY, "/v", RUN_VALUE_NAME);
    }

    static List<String> regDeleteCommand() {
        return List.of("reg.exe", "delete", RUN_KEY, "/v", RUN_VALUE_NAME, "/f");
    }

    static Optional<String> parseQueryValue(String output) {
        if (output == null || output.isBlank()) {
            return Optional.empty();
        }

        for (String line : output.split("\\R")) {
            String trimmed = line.trim();
            if (!trimmed.startsWith(RUN_VALUE_NAME)) {
                continue;
            }

            int typeIndex = trimmed.indexOf("REG_SZ");
            if (typeIndex < 0) {
                continue;
            }

            String value = trimmed.substring(typeIndex + "REG_SZ".length()).trim();
            return value.isEmpty() ? Optional.empty() : Optional.of(value);
        }

        return Optional.empty();
    }

    private static Optional<String> currentExecutablePath() {
        return ProcessHandle.current()
                .info()
                .command()
                .filter(command -> !command.isBlank());
    }

    private static Optional<String> queryRunValue() {
        try {
            ProcessResult result = runCommand(regQueryCommand());
            if (result.exitCode() != 0) {
                return Optional.empty();
            }

            return parseQueryValue(result.output());
        } catch (IOException exception) {
            log.warn("Could not query Windows bridge autostart registry value.", exception);
            return Optional.empty();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted while querying Windows bridge autostart registry value.", exception);
            return Optional.empty();
        }
    }

    private static void writeRunValue(String executablePath) throws IOException, InterruptedException {
        ProcessResult result = runCommand(regAddCommand(executablePath));
        if (result.exitCode() != 0) {
            log.warn("reg add failed for bridge autostart: {}", result.output());
        }
    }

    private static void deleteRunValue() throws IOException, InterruptedException {
        ProcessResult result = runCommand(regDeleteCommand());
        if (result.exitCode() != 0) {
            log.warn("reg delete failed for bridge autostart: {}", result.output());
        }
    }

    private static ProcessResult runCommand(List<String> command) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(command)
                .redirectErrorStream(true)
                .start();
        boolean finished = process.waitFor(REG_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (!finished) {
            process.destroyForcibly();
            return new ProcessResult(1, "Timed out running " + command.getFirst());
        }

        return new ProcessResult(process.exitValue(), output);
    }

    private static String quoteRunValue(String executablePath) {
        String normalizedPath = executablePath.trim();
        if (normalizedPath.startsWith("\"") && normalizedPath.endsWith("\"")) {
            return normalizedPath;
        }

        return "\"" + normalizedPath + "\"";
    }

    private static String normalizeRunValue(String value) {
        if (value == null) {
            return "";
        }

        String normalized = value.trim();
        if (normalized.startsWith("\"") && normalized.endsWith("\"") && normalized.length() >= 2) {
            normalized = normalized.substring(1, normalized.length() - 1);
        }
        return normalized;
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "")
                .toLowerCase(Locale.ROOT)
                .contains("win");
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    enum RegistryAction {
        WRITE,
        DELETE,
        LEAVE
    }

    private record ProcessResult(int exitCode, String output) {
    }
}
