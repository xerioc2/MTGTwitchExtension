package com.mtgtwitch.extension.detection.vision;

import com.mtgtwitch.extension.gamestate.GameCard;
import com.mtgtwitch.extension.scryfall.ScryfallCard;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OcrTitleMatcherTests {

    @Test
    void fuzzyTitleMatchingSelectsKnownCardName() {
        KnownGameCard crab = known(1, 79608, "Ruin Crab");
        KnownGameCard force = known(2, 54194, "Force of Will");

        OcrTitleMatcher.OcrMatch result = OcrTitleMatcher.bestNameMatch(
                "Ruin Cr ab",
                List.of(force, crab)
        ).orElseThrow();

        assertThat(result.card()).isEqualTo(crab);
        assertThat(result.score()).isGreaterThan(0.8);
    }

    @Test
    void normalizesPunctuationAndDiacritics() {
        assertThat(OcrTitleMatcher.normalizeName("Valentin Manes's Card"))
                .isEqualTo("valentin manes s card");
    }

    private static KnownGameCard known(int id, int catalogId, String name) {
        return new KnownGameCard(
                new GameCard(id, catalogId, "Battlefield", "Battlefield", 1, 1),
                new ScryfallCard(catalogId, name, "Creature", "", "", null, false),
                "BATTLEFIELD"
        );
    }
}
