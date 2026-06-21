package com.mtgtwitch.extension.detection;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.Instant;

public record DetectionRegion(
        String id,
        String channelId,
        String cardId,
        Integer catalogId,
        String cardName,
        String zone,
        String imageUrl,
        double confidence,
        DetectionBbox bbox,
        String source,
        Integer frameWidth,
        Integer frameHeight,
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Instant observedAt,
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Instant expiresAt
) {
}
