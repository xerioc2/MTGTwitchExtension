package com.mtgtwitch.extension.detection.vision;

import com.mtgtwitch.extension.detection.DetectionBbox;
import com.mtgtwitch.extension.detection.DetectionRegion;
import com.mtgtwitch.extension.detection.detector.DetectorContext;
import com.mtgtwitch.extension.gamestate.GameCard;
import com.mtgtwitch.extension.gamestate.GameState;
import com.mtgtwitch.extension.scryfall.ScryfallCard;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LocalVisionScreenCardDetectorTests {

    @Test
    void mapsScreenshotCropCoordinatesBackToTheOverlayFrame() {
        FrameSourceRouter frameSource = mock(FrameSourceRouter.class);
        OpenCvCardRectangleDetector rectangleDetector = mock(OpenCvCardRectangleDetector.class);
        KnownCardCatalog knownCardCatalog = mock(KnownCardCatalog.class);
        ImageHashTemplateMatcher templateMatcher = mock(ImageHashTemplateMatcher.class);
        OcrTitleMatcher ocrMatcher = mock(OcrTitleMatcher.class);
        DetectionConfidenceScorer confidenceScorer = mock(DetectionConfidenceScorer.class);
        LocalVisionDetectorProperties properties = mock(LocalVisionDetectorProperties.class);
        LocalVisionScreenCardDetector detector = new LocalVisionScreenCardDetector(
                frameSource,
                rectangleDetector,
                knownCardCatalog,
                templateMatcher,
                ocrMatcher,
                confidenceScorer,
                properties
        );

        Instant observedAt = Instant.parse("2026-08-08T12:00:00Z");
        BufferedImage image = new BufferedImage(200, 100, BufferedImage.TYPE_INT_RGB);
        DetectionBbox cropLocalBox = new DetectionBbox(0.20, 0.25, 0.40, 0.50);
        DetectedCardCandidate candidate = new DetectedCardCandidate(cropLocalBox, 0.9);
        KnownGameCard card = new KnownGameCard(
                new GameCard(17, 79608, "Battlefield", "Battlefield", 1, 1),
                new ScryfallCard(79608, "Ruin Crab", "Creature", "{U}", "", "https://example.test/card.jpg", false),
                "BATTLEFIELD"
        );
        ImageHashTemplateMatcher.TemplateMatch templateMatch =
                new ImageHashTemplateMatcher.TemplateMatch(card, 0.92);
        DetectionConfidenceScorer.ScoredCardMatch scoredMatch =
                new DetectionConfidenceScorer.ScoredCardMatch(card, 0.91, 0.92, 0.0);
        GameState gameState = mock(GameState.class);
        when(gameState.gameId()).thenReturn(55L);
        when(properties.templateEnabled()).thenReturn(true);
        when(properties.minConfidence()).thenReturn(0.72);
        when(properties.calibration()).thenReturn(new ScreenCalibration(0.25, 0.10, 0.50, 0.80));
        when(frameSource.capture()).thenReturn(Optional.of(new CapturedFrame(image, "SCREENSHOT", observedAt)));
        when(knownCardCatalog.resolve(gameState)).thenReturn(List.of(card));
        when(rectangleDetector.detect(image)).thenReturn(List.of(candidate));
        when(templateMatcher.bestMatch(any(), any())).thenReturn(Optional.of(templateMatch));
        when(ocrMatcher.bestMatch(any(), any())).thenReturn(Optional.empty());
        when(confidenceScorer.resolve(anyDouble(), any(), any())).thenReturn(Optional.of(scoredMatch));

        List<DetectionRegion> regions = detector.detect(
                gameState,
                new DetectorContext("channel", 55L, null, null, observedAt, "VISION", Map.of())
        );

        assertThat(regions).hasSize(1);
        DetectionBbox output = regions.getFirst().bbox();
        assertThat(output.x()).isCloseTo(0.35, org.assertj.core.data.Offset.offset(0.000001));
        assertThat(output.y()).isCloseTo(0.30, org.assertj.core.data.Offset.offset(0.000001));
        assertThat(output.w()).isCloseTo(0.20, org.assertj.core.data.Offset.offset(0.000001));
        assertThat(output.h()).isCloseTo(0.40, org.assertj.core.data.Offset.offset(0.000001));
        assertThat(regions.getFirst().catalogId()).isEqualTo(79608);
        assertThat(regions.getFirst().cardId()).isEqualTo("17");
    }
}
