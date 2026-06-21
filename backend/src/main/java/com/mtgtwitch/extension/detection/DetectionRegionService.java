package com.mtgtwitch.extension.detection;

import com.mtgtwitch.extension.gamestate.GameState;
import com.mtgtwitch.extension.gamestate.GameStateService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
public class DetectionRegionService {

    private final GameStateService gameStateService;
    private final Duration defaultTtl;

    public DetectionRegionService(
            GameStateService gameStateService,
            @Value("${screen-detections.ttl:PT2S}") Duration defaultTtl
    ) {
        this.gameStateService = gameStateService;
        this.defaultTtl = defaultTtl;
    }

    public GameState update(String requestChannelId, List<DetectionRegion> regions) {
        Instant now = Instant.now();
        List<DetectionRegion> normalizedRegions = regions == null ? List.of() : regions.stream()
                .map(region -> normalize(requestChannelId, region, now))
                .filter(this::isUsable)
                .toList();

        return gameStateService.updateDetectionRegions(normalizedRegions);
    }

    private DetectionRegion normalize(String requestChannelId, DetectionRegion region, Instant now) {
        if (region == null) {
            return null;
        }

        String channelId = firstText(region.channelId(), requestChannelId);
        Instant observedAt = region.observedAt() == null ? now : region.observedAt();
        Instant expiresAt = region.expiresAt() == null ? observedAt.plus(defaultTtl) : region.expiresAt();
        DetectionBbox bbox = region.bbox() == null ? null : region.bbox().clamped();

        return new DetectionRegion(
                firstText(region.id(), buildFallbackId(channelId, region, observedAt)),
                channelId,
                blankToNull(region.cardId()),
                positiveOrNull(region.catalogId()),
                firstText(region.cardName(), "Unknown card"),
                firstText(region.zone(), "UNKNOWN").toUpperCase(),
                blankToNull(region.imageUrl()),
                clampConfidence(region.confidence()),
                bbox,
                firstText(region.source(), "MANUAL").toUpperCase(),
                positiveOrNull(region.frameWidth()),
                positiveOrNull(region.frameHeight()),
                observedAt,
                expiresAt
        );
    }

    private boolean isUsable(DetectionRegion region) {
        return region != null
                && StringUtils.hasText(region.id())
                && StringUtils.hasText(region.channelId())
                && StringUtils.hasText(region.cardName())
                && region.bbox() != null
                && region.bbox().hasArea()
                && region.expiresAt() != null
                && region.observedAt() != null
                && region.expiresAt().isAfter(region.observedAt());
    }

    private String buildFallbackId(String channelId, DetectionRegion region, Instant observedAt) {
        String cardKey = firstText(region.cardId(), catalogIdText(region.catalogId()), region.cardName(), "unknown")
                .replaceAll("[^A-Za-z0-9_-]", "-");
        return firstText(channelId, "local") + "-" + cardKey + "-" + observedAt.toEpochMilli();
    }

    private static double clampConfidence(double confidence) {
        if (!Double.isFinite(confidence)) {
            return 0.0;
        }

        return Math.max(0.0, Math.min(1.0, confidence));
    }

    private static Integer positiveOrNull(Integer value) {
        return value != null && value > 0 ? value : null;
    }

    private static String catalogIdText(Integer catalogId) {
        return catalogId == null ? "" : String.valueOf(catalogId);
    }

    private static String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private static String firstText(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }

        return "";
    }
}
