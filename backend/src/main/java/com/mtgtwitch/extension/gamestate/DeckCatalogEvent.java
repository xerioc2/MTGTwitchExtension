package com.mtgtwitch.extension.gamestate;

import java.util.List;

public record DeckCatalogEvent(
        long gameId,
        String username,
        List<Integer> deckCatalogIds,
        String rawLine
) {
}
