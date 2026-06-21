package com.mtgtwitch.extension.detection.detector;

import java.time.Instant;
import java.util.Map;

public record DetectorContext(
        String channelId,
        Long gameId,
        Integer frameWidth,
        Integer frameHeight,
        Instant observedAt,
        String source,
        Map<String, String> calibration
) {
    public DetectorContext {
        calibration = calibration == null ? Map.of() : Map.copyOf(calibration);
    }
}
