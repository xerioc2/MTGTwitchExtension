package com.mtgtwitch.extension.detection.vision;

import com.mtgtwitch.extension.gamestate.GameCard;
import com.mtgtwitch.extension.scryfall.ScryfallCard;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class DetectionConfidenceScorerTests {

    private final DetectionConfidenceScorer scorer = new DetectionConfidenceScorer();

    @Test
    void agreementBetweenTemplateAndOcrRaisesConfidence() {
        KnownGameCard card = known(1, 79608, "Ruin Crab");

        DetectionConfidenceScorer.ScoredCardMatch match = scorer.resolve(
                0.8,
                Optional.of(new ImageHashTemplateMatcher.TemplateMatch(card, 0.82)),
                Optional.of(new OcrTitleMatcher.OcrMatch(card, 0.90, "Ruin Crab"))
        ).orElseThrow();

        assertThat(match.card()).isEqualTo(card);
        assertThat(match.confidence()).isGreaterThan(0.85);
    }

    @Test
    void similarConflictingSignalsAreRejected() {
        KnownGameCard left = known(1, 79608, "Ruin Crab");
        KnownGameCard right = known(2, 54194, "Force of Will");

        assertThat(scorer.resolve(
                0.9,
                Optional.of(new ImageHashTemplateMatcher.TemplateMatch(left, 0.78)),
                Optional.of(new OcrTitleMatcher.OcrMatch(right, 0.80, "Force of Will"))
        )).isEmpty();
    }

    private static KnownGameCard known(int id, int catalogId, String name) {
        return new KnownGameCard(
                new GameCard(id, catalogId, "Battlefield", "Battlefield", 1, 1),
                new ScryfallCard(catalogId, name, "Creature", "", "", null, false),
                "BATTLEFIELD"
        );
    }
}
