package com.mtgtwitch.extension.gamestate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mtgtwitch.extension.websocket.GameStateBroadcaster;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GameStateServiceTests {

    private final GameStateService gameStateService = new GameStateService(
            new GameStateBroadcaster(new ObjectMapper().findAndRegisterModules())
    );

    @Test
    void interleavedStatusEventsRetainCachedGamesAndFollowMostRecentEmitter() {
        gameStateService.apply(statusEvent(1001L, 2001L, 111001));
        gameStateService.apply(statusEvent(1002L, 2002L, 222001));
        GameState activeA = gameStateService.apply(statusEvent(1001L, 2001L, 111002));

        assertThat(activeA.gameId()).isEqualTo(1001L);
        assertThat(activeA.handCards()).extracting(GameCard::catalogId).containsExactly(111002);

        GameState activeB = gameStateService.updateDeckCatalogIds(new DeckCatalogEvent(
                1002L,
                "local",
                List.of(new DeckCard(900001, 4, false)),
                "raw"
        ));

        assertThat(activeB.gameId()).isEqualTo(1002L);
        assertThat(activeB.handCards()).extracting(GameCard::catalogId).containsExactly(222001);
        assertThat(activeB.deckCards()).containsExactly(new DeckCard(900001, 4, false));
    }

    @Test
    void cardZoneEventAppliesOnlyToActiveGame() {
        gameStateService.apply(statusEvent(1001L, 2001L, 111001));
        gameStateService.apply(statusEvent(1002L, 2002L, 222001));

        gameStateService.apply(new CardZoneEvent("Lightning Bolt", null, Zone.HAND, "raw"));

        GameState activeA = gameStateService.updateDeckCatalogIds(new DeckCatalogEvent(
                1001L,
                "local",
                List.of(new DeckCard(900001, 4, false)),
                "raw"
        ));
        assertThat(activeA.hand()).containsExactly("CatalogID 111001");

        GameState activeB = gameStateService.updateDeckCatalogIds(new DeckCatalogEvent(
                1002L,
                "local",
                List.of(new DeckCard(900002, 4, false)),
                "raw"
        ));
        assertThat(activeB.hand()).containsExactly("CatalogID 222001", "Lightning Bolt");
    }

    @Test
    void sameMatchNewGameEvictsPreviousGameAndDoesNotCarryDeckForward() {
        gameStateService.updateDeckCatalogIds(new DeckCatalogEvent(
                1001L,
                "local",
                List.of(new DeckCard(900001, 4, false)),
                "raw"
        ));
        gameStateService.apply(statusEvent(1001L, 2001L, 111001));

        GameState gameTwo = gameStateService.apply(statusEvent(1002L, 2001L, 222001));

        assertThat(gameTwo.gameId()).isEqualTo(1002L);
        assertThat(gameTwo.deckCatalogIds()).isEmpty();
        assertThat(gameTwo.deckCards()).isEmpty();
        assertThat(gameTwo.handCards()).extracting(GameCard::catalogId).containsExactly(222001);
    }

    @Test
    void lruCapEvictsOldestInactiveGame() {
        gameStateService.apply(statusEvent(1001L, 2001L, 111001));
        gameStateService.apply(statusEvent(1002L, 2002L, 222001));
        gameStateService.apply(statusEvent(1003L, 2003L, 333001));
        gameStateService.apply(statusEvent(1004L, 2004L, 444001));
        gameStateService.apply(statusEvent(1005L, 2005L, 555001));

        GameState oldGame = gameStateService.updateDeckCatalogIds(new DeckCatalogEvent(
                1001L,
                "local",
                List.of(new DeckCard(900001, 4, false)),
                "raw"
        ));

        assertThat(oldGame.gameId()).isEqualTo(1001L);
        assertThat(oldGame.handCards()).isEmpty();
        assertThat(oldGame.deckCards()).containsExactly(new DeckCard(900001, 4, false));
    }

    @Test
    void focusGameSwitchesCachedGameAndBroadcasts() {
        CountingBroadcaster broadcaster = new CountingBroadcaster(new ObjectMapper().findAndRegisterModules());
        GameStateService focusedService = new GameStateService(broadcaster);
        focusedService.apply(statusEvent(1001L, 2001L, 111001));
        focusedService.apply(statusEvent(1002L, 2002L, 222001));
        int broadcastCountBeforeFocus = broadcaster.broadcastCount();

        GameState focused = focusedService.focusGame(1001L);

        assertThat(focused.gameId()).isEqualTo(1001L);
        assertThat(focused.handCards()).extracting(GameCard::catalogId).containsExactly(111001);
        assertThat(broadcaster.broadcastCount()).isEqualTo(broadcastCountBeforeFocus + 1);
    }

    @Test
    void uncachedFocusBecomesActiveWhenThatGameArrives() {
        gameStateService.apply(statusEvent(1001L, 2001L, 111001));

        gameStateService.focusGame(1002L);
        GameState stillActiveA = gameStateService.apply(statusEvent(1001L, 2001L, 111002));
        assertThat(stillActiveA.gameId()).isEqualTo(1001L);
        assertThat(stillActiveA.handCards()).extracting(GameCard::catalogId).containsExactly(111002);

        GameState focusedB = gameStateService.apply(statusEvent(1002L, 2002L, 222001));
        assertThat(focusedB.gameId()).isEqualTo(1002L);
        assertThat(focusedB.handCards()).extracting(GameCard::catalogId).containsExactly(222001);
    }

    @Test
    void mostRecentEmitterDoesNotStealActiveGameFromCachedFocus() {
        gameStateService.apply(statusEvent(1001L, 2001L, 111001));
        gameStateService.apply(statusEvent(1002L, 2002L, 222001));
        gameStateService.focusGame(1001L);

        GameState stillFocusedA = gameStateService.apply(statusEvent(1002L, 2002L, 222002));

        assertThat(stillFocusedA.gameId()).isEqualTo(1001L);
        assertThat(stillFocusedA.handCards()).extracting(GameCard::catalogId).containsExactly(111001);
    }

    @Test
    void sameMatchSuccessionEvictsFocusedOldGameAndFallsBackToEmitter() {
        gameStateService.apply(statusEvent(1001L, 2001L, 111001));
        gameStateService.focusGame(1001L);

        GameState gameTwo = gameStateService.apply(statusEvent(1002L, 2001L, 222001));

        assertThat(gameTwo.gameId()).isEqualTo(1002L);
        assertThat(gameTwo.handCards()).extracting(GameCard::catalogId).containsExactly(222001);
    }

    @Test
    void catalogIdZeroObjectsAreFilteredFromZoneCards() {
        GameState gameState = gameStateService.apply(new GameStatusEvent(
                1001L,
                2001L,
                2001L,
                List.of(
                        new PlayerState(1, "local", 50, 5, 20),
                        new PlayerState(2, "opponent", 50, 5, 20)
                ),
                List.of(
                        new GameCard(1, 0, "Hand", "Hand", 1, 1),
                        new GameCard(2, 78632, "Hand", "Hand", 1, 1),
                        new GameCard(3, 0, "Battlefield", "Battlefield", 2, 2),
                        new GameCard(4, 82270, "Battlefield", "Battlefield", 2, 2)
                ),
                "raw"
        ));

        assertThat(gameState.hand()).doesNotContain("CatalogID 0");
        assertThat(gameState.handCards()).extracting(GameCard::catalogId).containsExactly(78632);
        assertThat(gameState.opponentBattlefieldCards()).extracting(GameCard::catalogId).containsExactly(82270);
    }

    private GameStatusEvent statusEvent(long gameId, long matchId, int localHandCatalogId) {
        return new GameStatusEvent(
                gameId,
                matchId,
                matchId,
                List.of(
                        new PlayerState(1, "local", 50, 5, 20),
                        new PlayerState(2, "opponent", 50, 5, 20)
                ),
                List.of(
                        new GameCard(1, localHandCatalogId, "Hand", "Hand", 1, 1),
                        new GameCard(2, localHandCatalogId + 1, "Battlefield", "Battlefield", 2, 2)
                ),
                "raw"
        );
    }

    private static class CountingBroadcaster extends GameStateBroadcaster {
        private int broadcastCount;

        private CountingBroadcaster(ObjectMapper objectMapper) {
            super(objectMapper);
        }

        @Override
        public void broadcast(GameState gameState) {
            broadcastCount++;
            super.broadcast(gameState);
        }

        private int broadcastCount() {
            return broadcastCount;
        }
    }
}
