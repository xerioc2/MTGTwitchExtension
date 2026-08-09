package com.mtgtwitch.extension.desktop;

import com.mtgtwitch.extension.detection.vision.CaptureMode;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

public record DetectorLocalSettings(
        boolean configured,
        boolean enabled,
        CaptureMode captureMode,
        double calibrationX,
        double calibrationY,
        double calibrationW,
        double calibrationH,
        String obsUrl,
        String obsPassword,
        String obsSourceName,
        boolean ocrEnabled,
        String ocrExecutable,
        boolean templateEnabled,
        double minConfidence,
        int scanSeconds
) {

    static final String CONFIGURED_KEY = "detector.local.configured";
    static final String ENABLED_KEY = "detector.local.enabled";
    static final String CAPTURE_MODE_KEY = "detector.local.capture.mode";
    static final String CALIBRATION_X_KEY = "detector.local.calibration.x";
    static final String CALIBRATION_Y_KEY = "detector.local.calibration.y";
    static final String CALIBRATION_W_KEY = "detector.local.calibration.w";
    static final String CALIBRATION_H_KEY = "detector.local.calibration.h";
    static final String OBS_URL_KEY = "detector.local.obs.url";
    static final String OBS_PASSWORD_KEY = "detector.local.obs.password";
    static final String OBS_SOURCE_NAME_KEY = "detector.local.obs.source-name";
    static final String OCR_ENABLED_KEY = "detector.local.ocr.enabled";
    static final String OCR_EXECUTABLE_KEY = "detector.local.ocr.executable";
    static final String TEMPLATE_ENABLED_KEY = "detector.local.template.enabled";
    static final String MIN_CONFIDENCE_KEY = "detector.local.min-confidence";
    static final String SCAN_SECONDS_KEY = "detector.local.scan-seconds";

    public DetectorLocalSettings {
        captureMode = captureMode == null ? CaptureMode.NONE : captureMode;
        calibrationX = clamp(calibrationX);
        calibrationY = clamp(calibrationY);
        calibrationW = clamp(calibrationW);
        calibrationH = clamp(calibrationH);
        obsUrl = textOrDefault(obsUrl, "ws://127.0.0.1:4455");
        obsPassword = obsPassword == null ? "" : obsPassword;
        obsSourceName = obsSourceName == null ? "" : obsSourceName.trim();
        ocrExecutable = textOrDefault(ocrExecutable, "tesseract");
        minConfidence = clamp(minConfidence);
        scanSeconds = Math.max(2, Math.min(60, scanSeconds));
    }

    public static DetectorLocalSettings defaults() {
        return new DetectorLocalSettings(
                false,
                false,
                CaptureMode.NONE,
                0.0,
                0.0,
                1.0,
                1.0,
                "ws://127.0.0.1:4455",
                "",
                "",
                false,
                "tesseract",
                true,
                0.72,
                5
        );
    }

    public static DetectorLocalSettings load(Path configPath) {
        try {
            Properties properties = BridgeLocalConfigStore.loadProperties(configPath);
            DetectorLocalSettings defaults = defaults();
            return new DetectorLocalSettings(
                    Boolean.parseBoolean(properties.getProperty(CONFIGURED_KEY, "false")),
                    Boolean.parseBoolean(properties.getProperty(ENABLED_KEY, "false")),
                    CaptureMode.fromConfig(properties.getProperty(CAPTURE_MODE_KEY, "none")),
                    readDouble(properties, CALIBRATION_X_KEY, defaults.calibrationX()),
                    readDouble(properties, CALIBRATION_Y_KEY, defaults.calibrationY()),
                    readDouble(properties, CALIBRATION_W_KEY, defaults.calibrationW()),
                    readDouble(properties, CALIBRATION_H_KEY, defaults.calibrationH()),
                    properties.getProperty(OBS_URL_KEY, defaults.obsUrl()),
                    properties.getProperty(OBS_PASSWORD_KEY, ""),
                    properties.getProperty(OBS_SOURCE_NAME_KEY, ""),
                    Boolean.parseBoolean(properties.getProperty(OCR_ENABLED_KEY, "false")),
                    properties.getProperty(OCR_EXECUTABLE_KEY, defaults.ocrExecutable()),
                    Boolean.parseBoolean(properties.getProperty(TEMPLATE_ENABLED_KEY, "true")),
                    readDouble(properties, MIN_CONFIDENCE_KEY, defaults.minConfidence()),
                    readInteger(properties, SCAN_SECONDS_KEY, defaults.scanSeconds())
            );
        } catch (IOException exception) {
            return defaults();
        }
    }

    public void save(Path configPath) throws IOException {
        Properties properties = BridgeLocalConfigStore.loadProperties(configPath);
        properties.setProperty(CONFIGURED_KEY, "true");
        properties.setProperty(ENABLED_KEY, Boolean.toString(enabled));
        properties.setProperty(CAPTURE_MODE_KEY, captureMode.name().toLowerCase());
        properties.setProperty(CALIBRATION_X_KEY, Double.toString(calibrationX));
        properties.setProperty(CALIBRATION_Y_KEY, Double.toString(calibrationY));
        properties.setProperty(CALIBRATION_W_KEY, Double.toString(calibrationW));
        properties.setProperty(CALIBRATION_H_KEY, Double.toString(calibrationH));
        properties.setProperty(OBS_URL_KEY, obsUrl);
        properties.setProperty(OBS_PASSWORD_KEY, obsPassword);
        properties.setProperty(OBS_SOURCE_NAME_KEY, obsSourceName);
        properties.setProperty(OCR_ENABLED_KEY, Boolean.toString(ocrEnabled));
        properties.setProperty(OCR_EXECUTABLE_KEY, ocrExecutable);
        properties.setProperty(TEMPLATE_ENABLED_KEY, Boolean.toString(templateEnabled));
        properties.setProperty(MIN_CONFIDENCE_KEY, Double.toString(minConfidence));
        properties.setProperty(SCAN_SECONDS_KEY, Integer.toString(scanSeconds));
        BridgeLocalConfigStore.saveProperties(configPath, properties);
    }

    public Map<String, Object> springProperties() {
        if (!configured) {
            return Map.of();
        }
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("screen-detections.detector.enabled", enabled);
        properties.put("screen-detections.detector.auto-run", enabled);
        properties.put("screen-detections.detector.mode", enabled ? "vision" : "none");
        if (enabled) {
            properties.put("screen-detections.enabled", true);
        }
        properties.put("screen-detections.detector.scan-interval", "PT" + scanSeconds + "S");
        properties.put("screen-detections.detector.capture.mode", captureMode.name().toLowerCase());
        properties.put("screen-detections.detector.calibration.x", calibrationX);
        properties.put("screen-detections.detector.calibration.y", calibrationY);
        properties.put("screen-detections.detector.calibration.w", calibrationW);
        properties.put("screen-detections.detector.calibration.h", calibrationH);
        properties.put("screen-detections.detector.obs.url", obsUrl);
        properties.put("screen-detections.detector.obs.password", obsPassword);
        properties.put("screen-detections.detector.obs.source-name", obsSourceName);
        properties.put("screen-detections.detector.ocr.enabled", ocrEnabled);
        properties.put("screen-detections.detector.ocr.executable", ocrExecutable);
        properties.put("screen-detections.detector.template.enabled", templateEnabled);
        properties.put("screen-detections.detector.min-confidence", minConfidence);
        return Map.copyOf(properties);
    }

    private static double readDouble(Properties properties, String key, double fallback) {
        try {
            return Double.parseDouble(properties.getProperty(key, Double.toString(fallback)));
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private static int readInteger(Properties properties, String key, int fallback) {
        try {
            return Integer.parseInt(properties.getProperty(key, Integer.toString(fallback)));
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private static double clamp(double value) {
        if (!Double.isFinite(value)) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(1.0, value));
    }

    private static String textOrDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
