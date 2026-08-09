package com.mtgtwitch.extension.desktop;

import com.mtgtwitch.extension.detection.vision.CaptureMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DetectorLocalSettingsTests {

    @TempDir
    Path temporaryDirectory;

    @Test
    void unconfiguredDefaultsDoNotOverrideEnvironmentConfiguration() {
        DetectorLocalSettings settings = DetectorLocalSettings.load(temporaryDirectory.resolve("config.properties"));

        assertThat(settings.configured()).isFalse();
        assertThat(settings.springProperties()).isEmpty();
    }

    @Test
    void enabledSettingsPersistAndMapToOptInSpringProperties() throws Exception {
        Path configPath = temporaryDirectory.resolve("config.properties");
        DetectorLocalSettings settings = new DetectorLocalSettings(
                true,
                true,
                CaptureMode.OBS,
                0.1,
                0.2,
                0.7,
                0.6,
                "ws://127.0.0.1:4455",
                "password",
                "MTGO",
                true,
                "C:\\Tools\\tesseract.exe",
                true,
                0.63,
                6
        );

        settings.save(configPath);
        DetectorLocalSettings loaded = DetectorLocalSettings.load(configPath);
        Map<String, Object> springProperties = loaded.springProperties();

        assertThat(loaded).isEqualTo(settings);
        assertThat(springProperties)
                .containsEntry("screen-detections.enabled", true)
                .containsEntry("screen-detections.detector.enabled", true)
                .containsEntry("screen-detections.detector.mode", "vision")
                .containsEntry("screen-detections.detector.capture.mode", "obs")
                .containsEntry("screen-detections.detector.auto-run", true);
    }
}
