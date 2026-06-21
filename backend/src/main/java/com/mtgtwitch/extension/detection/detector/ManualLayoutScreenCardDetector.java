package com.mtgtwitch.extension.detection.detector;

import com.mtgtwitch.extension.detection.DetectionBbox;
import com.mtgtwitch.extension.detection.DetectionRegion;
import com.mtgtwitch.extension.gamestate.GameCard;
import com.mtgtwitch.extension.gamestate.GameState;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Component
public class ManualLayoutScreenCardDetector implements ScreenCardDetector {

    private static final int MAX_ZONE_REGIONS = 5;

    @Override
    public List<DetectionRegion> detect(GameState currentGameState, DetectorContext detectorContext) {
        if (currentGameState == null) {
            return List.of();
        }

        List<DetectionRegion> regions = new ArrayList<>();
        addZoneRegions(regions, "HAND", currentGameState.handCards(), detectorContext, 0.14, 0.78);
        addZoneRegions(regions, "BATTLEFIELD", currentGameState.battlefieldCards(), detectorContext, 0.24, 0.36);
        addZoneRegions(regions, "GRAVEYARD", currentGameState.graveyardCards(), detectorContext, 0.82, 0.48);
        addZoneRegions(regions, "EXILE", currentGameState.exileCards(), detectorContext, 0.82, 0.30);
        return regions;
    }

    private void addZoneRegions(
            List<DetectionRegion> regions,
            String zone,
            List<GameCard> cards,
            DetectorContext context,
            double startX,
            double y
    ) {
        Instant observedAt = context.observedAt();
        int count = Math.min(cards.size(), MAX_ZONE_REGIONS);

        for (int index = 0; index < count; index++) {
            GameCard card = cards.get(index);
            if (card.catalogId() <= 0) {
                continue;
            }

            regions.add(new DetectionRegion(
                    "detector-manual-" + zone.toLowerCase() + "-" + card.id(),
                    context.channelId(),
                    String.valueOf(card.id()),
                    card.catalogId(),
                    "CatalogID " + card.catalogId(),
                    zone,
                    null,
                    0.65,
                    new DetectionBbox(startX + (index * 0.082), y, 0.07, 0.13),
                    context.source(),
                    context.frameWidth(),
                    context.frameHeight(),
                    observedAt,
                    observedAt.plusSeconds(3)
            ));
        }
    }
}
