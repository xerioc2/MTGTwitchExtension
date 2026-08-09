package com.mtgtwitch.extension.detection.vision;

import com.mtgtwitch.extension.detection.DetectionRegion;
import com.mtgtwitch.extension.detection.DetectionBbox;
import com.mtgtwitch.extension.detection.detector.DetectorContext;
import com.mtgtwitch.extension.detection.detector.ScreenCardDetector;
import com.mtgtwitch.extension.gamestate.GameState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.awt.image.BufferedImage;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Component
public class LocalVisionScreenCardDetector implements ScreenCardDetector {

    private static final Logger log = LoggerFactory.getLogger(LocalVisionScreenCardDetector.class);

    private final FrameSourceRouter frameSource;
    private final OpenCvCardRectangleDetector rectangleDetector;
    private final KnownCardCatalog knownCardCatalog;
    private final ImageHashTemplateMatcher templateMatcher;
    private final OcrTitleMatcher ocrMatcher;
    private final DetectionConfidenceScorer confidenceScorer;
    private final LocalVisionDetectorProperties properties;

    public LocalVisionScreenCardDetector(
            FrameSourceRouter frameSource,
            OpenCvCardRectangleDetector rectangleDetector,
            KnownCardCatalog knownCardCatalog,
            ImageHashTemplateMatcher templateMatcher,
            OcrTitleMatcher ocrMatcher,
            DetectionConfidenceScorer confidenceScorer,
            LocalVisionDetectorProperties properties
    ) {
        this.frameSource = frameSource;
        this.rectangleDetector = rectangleDetector;
        this.knownCardCatalog = knownCardCatalog;
        this.templateMatcher = templateMatcher;
        this.ocrMatcher = ocrMatcher;
        this.confidenceScorer = confidenceScorer;
        this.properties = properties;
    }

    @Override
    public List<DetectionRegion> detect(GameState currentGameState, DetectorContext detectorContext) {
        Optional<CapturedFrame> capturedFrame = frameSource.capture();
        if (capturedFrame.isEmpty()) {
            return List.of();
        }
        List<KnownGameCard> knownCards = knownCardCatalog.resolve(currentGameState);
        if (knownCards.isEmpty()) {
            return List.of();
        }

        CapturedFrame frame = capturedFrame.get();
        List<DetectedCardCandidate> candidates = rectangleDetector.detect(frame.image());
        List<DetectionRegion> regions = new ArrayList<>();
        Set<Integer> usedInstanceIds = new HashSet<>();
        int candidateIndex = 0;
        for (DetectedCardCandidate candidate : candidates) {
            List<KnownGameCard> availableCards = knownCards.stream()
                    .filter(card -> !usedInstanceIds.contains(card.gameCard().id()))
                    .toList();
            if (availableCards.isEmpty()) {
                break;
            }
            BufferedImage cardImage;
            try {
                cardImage = FrameImages.normalizeCardOrientation(
                        FrameImages.crop(frame.image(), candidate.bbox())
                );
            } catch (IllegalArgumentException exception) {
                continue;
            }

            Optional<ImageHashTemplateMatcher.TemplateMatch> templateMatch = properties.templateEnabled()
                    ? templateMatcher.bestMatch(cardImage, availableCards)
                    : Optional.empty();
            Optional<OcrTitleMatcher.OcrMatch> ocrMatch = ocrMatcher.bestMatch(cardImage, availableCards);
            Optional<DetectionConfidenceScorer.ScoredCardMatch> scored = confidenceScorer.resolve(
                    candidate.shapeConfidence(),
                    templateMatch,
                    ocrMatch
            );
            if (scored.isEmpty() || scored.get().confidence() < properties.minConfidence()) {
                continue;
            }

            DetectionConfidenceScorer.ScoredCardMatch match = scored.get();
            KnownGameCard card = match.card();
            usedInstanceIds.add(card.gameCard().id());
            Instant observedAt = detectorContext.observedAt() == null ? frame.capturedAt() : detectorContext.observedAt();
            DetectionBbox outputBbox = "SCREENSHOT".equals(frame.source())
                    ? properties.calibration().mapToParent(candidate.bbox())
                    : candidate.bbox();
            regions.add(new DetectionRegion(
                    regionId(currentGameState, card, candidateIndex++),
                    detectorContext.channelId(),
                    String.valueOf(card.gameCard().id()),
                    card.gameCard().catalogId(),
                    card.details().name(),
                    card.zone(),
                    card.details().imageUrl(),
                    match.confidence(),
                    outputBbox,
                    "SCREEN_SCANNER",
                    frame.image().getWidth(),
                    frame.image().getHeight(),
                    observedAt,
                    observedAt.plusSeconds(8)
            ));
        }
        log.debug("Local vision scan: source={}, candidates={}, matched={}", frame.source(), candidates.size(), regions.size());
        return List.copyOf(regions);
    }

    private static String regionId(GameState gameState, KnownGameCard card, int candidateIndex) {
        String gameId = gameState == null || gameState.gameId() == null ? "unknown" : gameState.gameId().toString();
        return "detector-vision-" + gameId + "-" + card.gameCard().id() + "-" + candidateIndex;
    }
}
