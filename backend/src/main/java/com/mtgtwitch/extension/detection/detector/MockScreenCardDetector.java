package com.mtgtwitch.extension.detection.detector;

import com.mtgtwitch.extension.detection.DetectionBbox;
import com.mtgtwitch.extension.detection.DetectionRegion;
import com.mtgtwitch.extension.gamestate.GameCard;
import com.mtgtwitch.extension.gamestate.GameState;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Component
public class MockScreenCardDetector implements ScreenCardDetector {

    private static final List<DetectionBbox> MOCK_BOXES = List.of(
            new DetectionBbox(0.22, 0.22, 0.075, 0.14),
            new DetectionBbox(0.34, 0.22, 0.075, 0.14),
            new DetectionBbox(0.46, 0.22, 0.075, 0.14)
    );

    @Override
    public List<DetectionRegion> detect(GameState currentGameState, DetectorContext detectorContext) {
        if (currentGameState == null) {
            return List.of();
        }

        Instant observedAt = detectorContext.observedAt();
        return currentGameState.battlefieldCards().stream()
                .filter(card -> card.catalogId() > 0)
                .limit(MOCK_BOXES.size())
                .map(card -> regionFor(card, detectorContext, observedAt))
                .toList();
    }

    private DetectionRegion regionFor(GameCard card, DetectorContext context, Instant observedAt) {
        int index = Math.floorMod(card.id(), MOCK_BOXES.size());
        return new DetectionRegion(
                "detector-mock-" + card.id(),
                context.channelId(),
                String.valueOf(card.id()),
                card.catalogId(),
                "CatalogID " + card.catalogId(),
                "BATTLEFIELD",
                null,
                0.9,
                MOCK_BOXES.get(index),
                context.source(),
                context.frameWidth(),
                context.frameHeight(),
                observedAt,
                observedAt.plusSeconds(3)
        );
    }
}
