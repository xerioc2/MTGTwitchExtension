package com.mtgtwitch.extension.gamestate;

import java.time.Instant;
import java.util.List;

public record GameState(
        List<String> hand,
        List<String> battlefield,
        List<String> graveyard,
        List<String> exile,
        Instant updatedAt
) {
}
