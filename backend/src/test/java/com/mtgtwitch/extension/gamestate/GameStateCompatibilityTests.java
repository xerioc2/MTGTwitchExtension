package com.mtgtwitch.extension.gamestate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mtgtwitch.extension.websocket.GameStateBroadcaster;
import org.junit.jupiter.api.Test;

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
        assertThat(json.has("updatedAt")).isTrue();
        assertThat(json.has("detectionRegions")).isTrue();
        assertThat(json.get("detectionRegions").isArray()).isTrue();
        assertThat(json.get("detectionRegions")).isEmpty();
    }
}
