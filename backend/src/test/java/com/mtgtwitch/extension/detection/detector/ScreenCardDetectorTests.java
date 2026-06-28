package com.mtgtwitch.extension.detection.detector;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mtgtwitch.extension.detection.DetectionRegion;
import com.mtgtwitch.extension.detection.DetectionRegionService;
import com.mtgtwitch.extension.gamestate.GameCard;
import com.mtgtwitch.extension.gamestate.GameState;
import com.mtgtwitch.extension.gamestate.GameStateService;
import com.mtgtwitch.extension.gamestate.PlayerState;
import com.mtgtwitch.extension.websocket.GameStateBroadcaster;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ScreenCardDetectorTests {

    private static final Instant OBSERVED_AT = Instant.parse("2026-06-20T20:15:22Z");

    @Test
    void mockDetectorProducesValidRegionsForCurrentBattlefieldCards() {
        GameState gameState = gameStateWithCards(
                List.of(),
                List.of(new GameCard(428, 122024, "Battlefield", "Battlefield", 1, 1)),
                List.of(),
                List.of()
        );

        List<DetectionRegion> regions = new MockScreenCardDetector().detect(gameState, context("MOCK"));

        assertThat(regions).hasSize(1);
        DetectionRegion region = regions.getFirst();
        assertThat(region.cardId()).isEqualTo("428");
        assertThat(region.catalogId()).isEqualTo(122024);
        assertThat(region.zone()).isEqualTo("BATTLEFIELD");
        assertThat(region.channelId()).isEqualTo("xerioc2");
        assertThat(region.bbox().x()).isBetween(0.0, 1.0);
        assertThat(region.bbox().y()).isBetween(0.0, 1.0);
        assertThat(region.bbox().w()).isBetween(0.0, 1.0);
        assertThat(region.bbox().h()).isBetween(0.0, 1.0);
    }

    @Test
    void manualDetectorProducesNormalizedRegionsForKnownZones() {
        GameState gameState = gameStateWithCards(
                List.of(new GameCard(101, 111111, "Hand", "Hand", 1, 1)),
                List.of(new GameCard(202, 222222, "Battlefield", "Battlefield", 1, 1)),
                List.of(new GameCard(303, 333333, "Graveyard", "Graveyard", 1, 1)),
                List.of(new GameCard(404, 444444, "Exile", "Exile", 1, 1))
        );

        List<DetectionRegion> regions = new ManualLayoutScreenCardDetector().detect(gameState, context("MANUAL"));

        assertThat(regions).extracting(DetectionRegion::zone)
                .containsExactly("HAND", "BATTLEFIELD", "GRAVEYARD", "EXILE");
        assertThat(regions).allSatisfy(region -> {
            assertThat(region.bbox().x()).isBetween(0.0, 1.0);
            assertThat(region.bbox().y()).isBetween(0.0, 1.0);
            assertThat(region.bbox().w()).isBetween(0.0, 1.0);
            assertThat(region.bbox().h()).isBetween(0.0, 1.0);
        });
    }

    @Test
    void detectorsSkipCardsMissingCatalogIds() {
        GameState gameState = gameStateWithCards(
                List.of(new GameCard(101, 0, "Hand", "Hand", 1, 1)),
                List.of(new GameCard(202, 0, "Battlefield", "Battlefield", 1, 1)),
                List.of(),
                List.of()
        );

        assertThat(new MockScreenCardDetector().detect(gameState, context("MOCK"))).isEmpty();
        assertThat(new ManualLayoutScreenCardDetector().detect(gameState, context("MANUAL"))).isEmpty();
    }

    @Test
    void publisherIsDisabledByDefault() {
        TestHarness harness = new TestHarness(false, false, DetectorMode.NONE, Duration.ofSeconds(1));

        DetectorPublishResult result = harness.publisher.publishOnce("xerioc2");

        assertThat(result.published()).isFalse();
        assertThat(result.status()).isEqualTo("disabled");
        assertThat(harness.gameStateService.snapshot().detectionRegions()).isEmpty();
    }

    @Test
    void publisherThrottlesDetectorRuns() {
        TestHarness harness = new TestHarness(true, true, DetectorMode.MANUAL, Duration.ofSeconds(1));

        DetectorPublishResult first = harness.publisher.publishOnce("xerioc2");
        DetectorPublishResult second = harness.publisher.publishOnce("xerioc2");

        assertThat(first.published()).isTrue();
        assertThat(second.published()).isFalse();
        assertThat(second.status()).isEqualTo("throttled");
    }

    private static DetectorContext context(String source) {
        return new DetectorContext("xerioc2", 950656092L, null, null, OBSERVED_AT, source, Map.of());
    }

    private static GameState gameStateWithCards(
            List<GameCard> handCards,
            List<GameCard> battlefieldCards,
            List<GameCard> graveyardCards,
            List<GameCard> exileCards
    ) {
        return new GameState(
                handCards.stream().map(GameCard::displayName).toList(),
                battlefieldCards.stream().map(GameCard::displayName).toList(),
                graveyardCards.stream().map(GameCard::displayName).toList(),
                exileCards.stream().map(GameCard::displayName).toList(),
                handCards,
                battlefieldCards,
                graveyardCards,
                exileCards,
                List.<PlayerState>of(),
                950656092L,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                OBSERVED_AT
        );
    }

    private static final class TestHarness {
        private final GameStateService gameStateService;
        private final ScreenDetectorPublisher publisher;

        private TestHarness(
                boolean detectionsEnabled,
                boolean detectorEnabled,
                DetectorMode mode,
                Duration minimumInterval
        ) {
            ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
            gameStateService = new GameStateService(new GameStateBroadcaster(objectMapper));
            DetectionRegionService detectionRegionService = new DetectionRegionService(
                    gameStateService,
                    Duration.ofSeconds(2)
            );
            publisher = new ScreenDetectorPublisher(
                    detectionRegionService,
                    gameStateService,
                    new MockScreenCardDetector(),
                    new ManualLayoutScreenCardDetector(),
                    detectionsEnabled,
                    detectorEnabled,
                    mode,
                    minimumInterval,
                    "xerioc2",
                    Clock.fixed(OBSERVED_AT, ZoneId.of("UTC"))
            );
        }
    }
}
