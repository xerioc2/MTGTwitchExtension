package com.mtgtwitch.extension.detection.vision;

import java.awt.image.BufferedImage;
import java.time.Instant;

public record CapturedFrame(BufferedImage image, String source, Instant capturedAt) {

    public CapturedFrame {
        if (image == null) {
            throw new IllegalArgumentException("Captured frame image is required.");
        }
        source = source == null || source.isBlank() ? "UNKNOWN" : source.trim().toUpperCase();
        capturedAt = capturedAt == null ? Instant.now() : capturedAt;
    }
}
