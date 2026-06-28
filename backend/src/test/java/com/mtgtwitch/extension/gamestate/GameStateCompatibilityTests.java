package com.mtgtwitch.extension.gamestate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mtgtwitch.extension.websocket.GameStateBroadcaster;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GameStateCompatibilityTests {

    @Test
    void defaultSnapshotKeepsV1FieldsAndAddsEmptyDetectionRegions() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        GameStateService gameStateService = new GameStateService(new GameStateBroadcaster(objectMapper));

        JsonNode json = objectMapper.valueToTree(gameStateService.snapshot());

        assertThat(json.has("hand")).isTrue();
        assertThat(json.has("battlefield")).isTrue();
        assertThat(json.has("graveyard")).isTrue();
        assertThat(json.has("exile")).isTrue();
        assertThat(json.has("handCards")).isTrue();
        assertThat(json.has("battlefieldCards")).isTrue();
        assertThat(json.has("graveyardCards")).isTrue();
        assertThat(json.has("exileCards")).isTrue();
        assertThat(json.has("players")).isTrue();
        assertThat(json.has("gameId")).isTrue();
        assertThat(json.has("deckCatalogIds")).isTrue();
        assertThat(json.has("deckCards")).isTrue();
        assertThat(json.get("deckCards").isArray()).isTrue();
        assertThat(json.get("deckCards")).isEmpty();
        assertThat(json.has("updatedAt")).isTrue();
        assertThat(json.has("detectionRegions")).isTrue();
        assertThat(json.get("detectionRegions").isArray()).isTrue();
        assertThat(json.get("detectionRegions")).isEmpty();
        assertThat(json.has("opponentBattlefieldCards")).isTrue();
        assertThat(json.has("opponentGraveyardCards")).isTrue();
        assertThat(json.has("opponentExileCards")).isTrue();
        assertThat(json.get("opponentBattlefieldCards").isArray()).isTrue();
        assertThat(json.get("opponentGraveyardCards").isArray()).isTrue();
        assertThat(json.get("opponentExileCards").isArray()).isTrue();
        assertThat(json.get("opponentBattlefieldCards")).isEmpty();
        assertThat(json.get("opponentGraveyardCards")).isEmpty();
        assertThat(json.get("opponentExileCards")).isEmpty();
    }

    @Test
    void opponentCardsTrackedFromGameStatusEvent() {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        GameStateService gameStateService = new GameStateService(new GameStateBroadcaster(objectMapper));

        GameState gameState = gameStateService.apply(new GameStatusEvent(
                951234567L,
                1L,
                1L,
                List.of(
                        new PlayerState(1, "local", 40, 7, 20),
                        new PlayerState(2, "opponent", 40, 7, 20)
                ),
                List.of(
                        new GameCard(101, 111001, "Battlefield", "Battlefield", 1, 1),
                        new GameCard(201, 222001, "Battlefield", "Battlefield", 2, 2),
                        new GameCard(202, 222002, "Graveyard", "Graveyard", 2, 2),
                        new GameCard(203, 222003, "Exile", "Exile", 2, 2),
                        new GameCard(204, 222004, "Hand", "Hand", 2, 2)
                ),
                "raw"
        ));

        assertThat(gameState.battlefieldCards())
                .extracting(GameCard::catalogId)
                .containsExactly(111001);
        assertThat(gameState.opponentBattlefieldCards())
                .extracting(GameCard::catalogId)
                .containsExactly(222001);
        assertThat(gameState.opponentGraveyardCards())
                .extracting(GameCard::catalogId)
                .containsExactly(222002);
        assertThat(gameState.opponentExileCards())
                .extracting(GameCard::catalogId)
                .containsExactly(222003);
        assertThat(gameState.opponentBattlefieldCards())
                .extracting(GameCard::catalogId)
                .doesNotContain(222004);
        assertThat(gameState.opponentGraveyardCards())
                .extracting(GameCard::catalogId)
                .doesNotContain(222004);
        assertThat(gameState.opponentExileCards())
                .extracting(GameCard::catalogId)
                .doesNotContain(222004);
    }
}
