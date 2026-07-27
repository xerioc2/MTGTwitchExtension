package com.mtgtwitch.extension.desktop;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class ObsAutoLaunchSetup {

    static final String SCRIPT_NAME = "mtgo-twitch-bridge-launcher.lua";
    static final String SIDECAR_NAME = "mtgo-twitch-bridge-launcher.path.txt";

    private static final Logger log = LoggerFactory.getLogger(ObsAutoLaunchSetup.class);

    private ObsAutoLaunchSetup() {
    }

    public static SetupResult installDefault() {
        Optional<Path> executablePath = currentExecutablePath();
        if (executablePath.isEmpty()) {
            log.warn("Could not resolve current bridge executable path for OBS auto-launch setup.");
            return SetupResult.failed("Could not resolve the current bridge executable path.");
        }

        Optional<Path> obsStudioDirectory = defaultObsStudioDirectory();
        if (obsStudioDirectory.isEmpty() || !Files.isDirectory(obsStudioDirectory.get())) {
            return SetupResult.obsNotFound();
        }

        Optional<Path> scriptPath = resolveBundledScript(executablePath.get());
        if (scriptPath.isEmpty()) {
            log.warn("Could not find bundled OBS auto-launch script near {}.", executablePath.get());
            return SetupResult.scriptNotFound();
        }

        return install(obsStudioDirectory.get(), scriptPath.get(), executablePath.get());
    }

    static SetupResult install(Path obsStudioDirectory, Path sourceScript, Path executablePath) {
        if (obsStudioDirectory == null || !Files.isDirectory(obsStudioDirectory)) {
            return SetupResult.obsNotFound();
        }
        if (sourceScript == null || !Files.isRegularFile(sourceScript)) {
            return SetupResult.scriptNotFound();
        }
        if (executablePath == null) {
            return SetupResult.failed("Could not resolve the current bridge executable path.");
        }

        Path scriptsDirectory = obsStudioDirectory.resolve("scripts");
        Path targetScript = scriptsDirectory.resolve(SCRIPT_NAME);
        Path targetSidecar = scriptsDirectory.resolve(SIDECAR_NAME);
        try {
            Files.createDirectories(scriptsDirectory);
            Files.copy(sourceScript, targetScript, StandardCopyOption.REPLACE_EXISTING);
            Files.writeString(targetSidecar, sidecarContent(executablePath), StandardCharsets.UTF_8);
            return SetupResult.installed(targetScript, targetSidecar);
        } catch (IOException exception) {
            log.warn("Could not install OBS auto-launch script.", exception);
            return SetupResult.failed("Could not copy the OBS auto-launch script.");
        }
    }

    static Optional<Path> defaultObsStudioDirectory() {
        String appData = System.getenv("APPDATA");
        if (!StringUtils.hasText(appData)) {
            return Optional.empty();
        }

        return Optional.of(Path.of(appData, "obs-studio"));
    }

    static Optional<Path> currentExecutablePath() {
        return ProcessHandle.current()
                .info()
                .command()
                .filter(command -> !command.isBlank())
                .map(Path::of)
                .map(path -> path.toAbsolutePath().normalize());
    }

    static Optional<Path> resolveBundledScript(Path executablePath) {
        return scriptCandidates(executablePath, Path.of("").toAbsolutePath())
                .stream()
                .filter(Files::isRegularFile)
                .findFirst();
    }

    static List<Path> scriptCandidates(Path executablePath, Path workingDirectory) {
        List<Path> candidates = new ArrayList<>();
        Path executableDirectory = executablePath == null ? null : executablePath.toAbsolutePath().normalize().getParent();
        if (executableDirectory != null) {
            candidates.add(executableDirectory.resolve("obs").resolve(SCRIPT_NAME));
            candidates.add(executableDirectory.resolve("app").resolve("obs").resolve(SCRIPT_NAME));
        }

        if (workingDirectory != null) {
            candidates.add(workingDirectory.toAbsolutePath().normalize().resolve("obs").resolve(SCRIPT_NAME));
        }
        return candidates;
    }

    static String sidecarContent(Path executablePath) {
        return executablePath.toAbsolutePath().normalize() + System.lineSeparator();
    }

    public record SetupResult(
            SetupStatus status,
            Path scriptPath,
            Path sidecarPath,
            String message
    ) {
        static SetupResult installed(Path scriptPath, Path sidecarPath) {
            return new SetupResult(
                    SetupStatus.INSTALLED,
                    scriptPath,
                    sidecarPath,
                    "OBS auto-launch script copied."
            );
        }

        static SetupResult obsNotFound() {
            return new SetupResult(
                    SetupStatus.OBS_NOT_FOUND,
                    null,
                    null,
                    "OBS was not detected. Open OBS once, then try again."
            );
        }

        static SetupResult scriptNotFound() {
            return new SetupResult(
                    SetupStatus.SCRIPT_NOT_FOUND,
                    null,
                    null,
                    "The bundled OBS auto-launch script was not found."
            );
        }

        static SetupResult failed(String message) {
            return new SetupResult(SetupStatus.FAILED, null, null, message);
        }
    }

    public enum SetupStatus {
        INSTALLED,
        OBS_NOT_FOUND,
        SCRIPT_NOT_FOUND,
        FAILED
    }
}
