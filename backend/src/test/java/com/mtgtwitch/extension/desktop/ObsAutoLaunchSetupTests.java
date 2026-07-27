package com.mtgtwitch.extension.desktop;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ObsAutoLaunchSetupTests {

    @TempDir
    Path tempDir;

    @Test
    void installCopiesScriptAndWritesExecutableSidecar() throws Exception {
        Path obsStudioDirectory = tempDir.resolve("obs-studio");
        Files.createDirectories(obsStudioDirectory);
        Path sourceScript = tempDir.resolve(ObsAutoLaunchSetup.SCRIPT_NAME);
        Files.writeString(sourceScript, "obs = obslua", StandardCharsets.UTF_8);
        Path executablePath = tempDir.resolve("MTGO Twitch Bridge").resolve("MTGO Twitch Bridge.exe");

        ObsAutoLaunchSetup.SetupResult result = ObsAutoLaunchSetup.install(
                obsStudioDirectory,
                sourceScript,
                executablePath
        );

        Path targetScript = obsStudioDirectory.resolve("scripts").resolve(ObsAutoLaunchSetup.SCRIPT_NAME);
        Path targetSidecar = obsStudioDirectory.resolve("scripts").resolve(ObsAutoLaunchSetup.SIDECAR_NAME);
        assertThat(result.status()).isEqualTo(ObsAutoLaunchSetup.SetupStatus.INSTALLED);
        assertThat(result.scriptPath()).isEqualTo(targetScript);
        assertThat(result.sidecarPath()).isEqualTo(targetSidecar);
        assertThat(targetScript).hasContent("obs = obslua");
        assertThat(targetSidecar).hasContent(ObsAutoLaunchSetup.sidecarContent(executablePath));
    }

    @Test
    void installDoesNotCreateObsStudioParentWhenMissing() {
        Path missingObsStudioDirectory = tempDir.resolve("obs-studio");
        Path sourceScript = tempDir.resolve(ObsAutoLaunchSetup.SCRIPT_NAME);
        Path executablePath = tempDir.resolve("MTGO Twitch Bridge.exe");

        ObsAutoLaunchSetup.SetupResult result = ObsAutoLaunchSetup.install(
                missingObsStudioDirectory,
                sourceScript,
                executablePath
        );

        assertThat(result.status()).isEqualTo(ObsAutoLaunchSetup.SetupStatus.OBS_NOT_FOUND);
        assertThat(missingObsStudioDirectory).doesNotExist();
    }

    @Test
    void scriptCandidatesCheckPortableAndInstalledLocations() {
        Path executablePath = Path.of("C:\\Bridge\\MTGO Twitch Bridge.exe");
        Path workingDirectory = Path.of("C:\\Repo");

        assertThat(ObsAutoLaunchSetup.scriptCandidates(executablePath, workingDirectory))
                .containsExactly(
                        Path.of("C:\\Bridge\\obs\\mtgo-twitch-bridge-launcher.lua"),
                        Path.of("C:\\Bridge\\app\\obs\\mtgo-twitch-bridge-launcher.lua"),
                        Path.of("C:\\Repo\\obs\\mtgo-twitch-bridge-launcher.lua")
                );
    }
}
