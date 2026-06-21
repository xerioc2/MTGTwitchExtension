package com.mtgtwitch.extension.detection;

import com.mtgtwitch.extension.gamestate.GameCard;
import com.mtgtwitch.extension.gamestate.GameState;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Component
public class MockDetectionProvider {

    private static final List<DetectionBbox> MOCK_BOXES = List.of(
            new DetectionBbox(0.23, 0.24, 0.075, 0.14),
            new DetectionBbox(0.36, 0.24, 0.075, 0.14),
            new DetectionBbox(0.49, 0.24, 0.075, 0.14)
    );

    public List<DetectionRegion> generate(String channelId, GameState gameState) {
        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(8);
        List<DetectionRegion> regions = new ArrayList<>();
        List<GameCard> cards = gameState == null ? List.of() : gameState.battlefieldCards();

        for (int index = 0; index < Math.min(cards.size(), MOCK_BOXES.size()); index++) {
            GameCard card = cards.get(index);
            regions.add(new DetectionRegion(
                    "mock-" + card.id(),
                    channelId,
                    String.valueOf(card.id()),
                    card.catalogId(),
                    "CatalogID " + card.catalogId(),
                    "BATTLEFIELD",
                    null,
                    0.93,
                    MOCK_BOXES.get(index),
                    "MOCK",
                    1920,
                    1080,
                    now,
                    expiresAt
            ));
        }

        if (regions.isEmpty()) {
            regions.add(new DetectionRegion(
                    "mock-lightning-bolt",
                    channelId,
                    null,
                    null,
                    "Lightning Bolt",
                    "UNKNOWN",
                    null,
                    0.88,
                    MOCK_BOXES.getFirst(),
                    "MOCK",
                    1920,
                    1080,
                    now,
                    expiresAt
            ));
        }

        return regions;
    }
}
