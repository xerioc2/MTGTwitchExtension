package com.mtgtwitch.extension.detection.detector;

import java.util.Locale;

public enum DetectorMode {
    NONE,
    MOCK,
    MANUAL;

    public static DetectorMode fromConfig(String value) {
        if (value == null || value.isBlank()) {
            return NONE;
        }

        try {
            return DetectorMode.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return NONE;
        }
    }
}
