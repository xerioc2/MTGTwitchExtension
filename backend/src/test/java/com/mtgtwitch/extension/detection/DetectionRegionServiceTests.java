package com.mtgtwitch.extension.detection;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mtgtwitch.extension.gamestate.GameCard;
import com.mtgtwitch.extension.gamestate.GameState;
import com.mtgtwitch.extension.gamestate.GameStateService;
import com.mtgtwitch.extension.gamestate.PlayerState;
import com.mtgtwitch.extension.websocket.GameStateBroadcaster;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class DetectionRegionServiceTests {

    private final GameStateService gameStateService = new GameStateService(
            new GameStateBroadcaster(new ObjectMapper().findAndRegisterModules())
    );
    private final DetectionRegionService detectionRegionService = new DetectionRegionService(
            gameStateService,
            java.time.Duration.ofSeconds(2)
    );

    @Test
    void clampsBoundingBoxAndConfidence() {
        Instant now = Instant.now();

        GameState gameState = detectionRegionService.update("xerioc2", List.of(new DetectionRegion(
                "region-1",
                null,
                "428",
                122024,
                "Otawara, Soaring City",
                "battlefield",
                null,
                2.4,
                new DetectionBbox(0.9, -0.5, 0.4, 0.25),
                "mock",
                -1,
                0,
                now,
                now.plusSeconds(5)
        )));

        assertThat(gameState.detectionRegions()).hasSize(1);
        DetectionRegion region = gameState.detectionRegions().getFirst();
        assertThat(region.channelId()).isEqualTo("xerioc2");
        assertThat(region.cardId()).isEqualTo("428");
        assertThat(region.catalogId()).isEqualTo(122024);
        assertThat(region.zone()).isEqualTo("BATTLEFIELD");
        assertThat(region.source()).isEqualTo("MOCK");
        assertThat(region.confidence()).isEqualTo(1.0);
        assertThat(region.frameWidth()).isNull();
        assertThat(region.frameHeight()).isNull();
        assertThat(region.bbox().x()).isEqualTo(0.9);
        assertThat(region.bbox().y()).isEqualTo(0.0);
        assertThat(region.bbox().w()).isCloseTo(0.1, within(0.000001));
        assertThat(region.bbox().h()).isEqualTo(0.25);
    }

    @Test
    void omitsStaleRegionsFromSnapshot() {
        Instant now = Instant.now();

        GameState gameState = detectionRegionService.update("xerioc2", List.of(new DetectionRegion(
                "region-1",
                "xerioc2",
                "428",
                122024,
                "Otawara, Soaring City",
                "BATTLEFIELD",
                null,
                0.8,
                new DetectionBbox(0.2, 0.2, 0.1, 0.1),
                "MOCK",
                1920,
                1080,
                now.minusSeconds(5),
                now.minusSeconds(1)
        )));

        assertThat(gameState.detectionRegions()).isEmpty();
        assertThat(gameStateService.snapshot().detectionRegions()).isEmpty();
    }

    @Test
    void rejectsRegionsWithoutUsableArea() {
        Instant now = Instant.now();

        GameState gameState = detectionRegionService.update("xerioc2", List.of(new DetectionRegion(
                "region-1",
                "xerioc2",
                "428",
                122024,
                "Otawara, Soaring City",
                "BATTLEFIELD",
                null,
                0.8,
                new DetectionBbox(0.2, 0.2, 0.0, 0.1),
                "MOCK",
                1920,
                1080,
                now,
                now.plusSeconds(5)
        )));

        assertThat(gameState.detectionRegions()).isEmpty();
    }

    @Test
    void mockDetectionKeepsInstanceIdAndCatalogIdSeparate() {
        Instant now = Instant.now();
        GameState gameState = new GameState(
                List.of(),
                List.of("CatalogID 122024"),
                List.of(),
                List.of(),
                List.of(),
                List.of(new GameCard(428, 122024, "Battlefield", "Battlefield", 1, 1)),
                List.of(),
                List.of(),
                List.<PlayerState>of(),
                950656092L,
                List.of(),
                List.of(),
                now
        );

        DetectionRegion region = new MockDetectionProvider().generate("xerioc2", gameState).getFirst();

        assertThat(region.cardId()).isEqualTo("428");
        assertThat(region.catalogId()).isEqualTo(122024);
    }

    @Test
    void instantFieldsSerializeAsIsoStrings() throws Exception {
        Instant now = Instant.parse("2026-06-20T20:15:22.123Z");
        DetectionRegion region = new DetectionRegion(
                "region-1",
                "xerioc2",
                "428",
                122024,
                "Otawara, Soaring City",
                "BATTLEFIELD",
                null,
                0.8,
                new DetectionBbox(0.2, 0.2, 0.1, 0.1),
                "MOCK",
                1920,
                1080,
                now,
                now.plusMillis(1500)
        );

        String json = new ObjectMapper().findAndRegisterModules().writeValueAsString(region);

        assertThat(json).contains("\"observedAt\":\"2026-06-20T20:15:22.123Z\"");
        assertThat(json).contains("\"expiresAt\":\"2026-06-20T20:15:23.623Z\"");
    }
}
