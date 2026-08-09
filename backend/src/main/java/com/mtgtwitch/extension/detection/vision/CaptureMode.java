package com.mtgtwitch.extension.detection.vision;

import java.util.Locale;

public enum CaptureMode {
    NONE,
    SCREENSHOT,
    OBS;

    public static CaptureMode fromConfig(String value) {
        if (value == null || value.isBlank()) {
            return NONE;
        }

        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return NONE;
        }
    }
}
