package com.mtgtwitch.extension.gamestate;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class MtgoLogParserDeckTests {

    private final MtgoLogParserService parser = new MtgoLogParserService(null, new ObjectMapper());

    @Test
    void parsesDeckUsedInGameWithQuantityAndSideboardFlag() {
        Optional<DeckCatalogEvent> event = parser.parseDeckCatalogEvent("""
                20:36:01 [INF] (Twitch Info|Username: DB_xerioc Deck Used in Game ID: 954615110) [{"CatalogId":48404,"Quantity":2,"Annotation":"NotSet","InSideboard":true},{"CatalogId":126449,"Quantity":4,"Annotation":"NotSet","InSideboard":false}]
                """.trim());

        assertThat(event).hasValueSatisfying(deckEvent -> {
            assertThat(deckEvent.gameId()).isEqualTo(954615110);
            assertThat(deckEvent.username()).isEqualTo("DB_xerioc");
            assertThat(deckEvent.deckCards()).containsExactly(
                    new DeckCard(48404, 2, true),
                    new DeckCard(126449, 4, false)
            );
        });
    }

    @Test
    void parsesDeckUsedToJoinEvent() {
        Optional<DeckCatalogEvent> event = parser.parseDeckCatalogEvent("""
                20:30:00 [INF] (Twitch Info|Username: DB_xerioc Deck Used to Join Event ID:12845664) [{"CatalogId":106607,"Quantity":3,"Annotation":"NotSet","InSideboard":false}]
                """.trim());

        assertThat(event).hasValueSatisfying(deckEvent -> {
            assertThat(deckEvent.gameId()).isEqualTo(12845664);
            assertThat(deckEvent.username()).isEqualTo("DB_xerioc");
            assertThat(deckEvent.deckCards()).containsExactly(new DeckCard(106607, 3, false));
        });
    }

    @Test
    void returnsEmptyForNullGameDeckEvent() {
        Optional<DeckCatalogEvent> event = parser.parseDeckCatalogEvent("""
                20:36:01 [INF] (Twitch Info|Username: DB_xerioc Deck Used in Game ID: 954615110) [NULL]
                """.trim());

        assertThat(event).isEmpty();
    }

    @Test
    void separatesMainDeckCardsFromSideboardCards() {
        Optional<DeckCatalogEvent> event = parser.parseDeckCatalogEvent("""
                20:36:01 [INF] (Twitch Info|Username: DB_xerioc Deck Used in Game ID: 954615110) [{"CatalogId":111,"Quantity":4,"Annotation":"NotSet","InSideboard":false},{"CatalogId":222,"Quantity":2,"Annotation":"NotSet","InSideboard":true},{"CatalogId":333,"Quantity":1,"Annotation":"NotSet","InSideboard":false}]
                """.trim());

        assertThat(event).hasValueSatisfying(deckEvent -> {
            assertThat(deckEvent.deckCards().stream()
                    .filter(deckCard -> !deckCard.inSideboard())
                    .map(DeckCard::catalogId)
                    .toList())
                    .containsExactly(111, 333);
            assertThat(deckEvent.deckCards().stream()
                    .filter(DeckCard::inSideboard)
                    .map(DeckCard::catalogId)
                    .toList())
                    .containsExactly(222);
        });
    }
}
