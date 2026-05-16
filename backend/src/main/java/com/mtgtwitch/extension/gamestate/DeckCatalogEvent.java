package com.mtgtwitch.extension.gamestate;

import java.util.List;

public record DeckCatalogEvent(
        long gameId,
        List<Integer> deckCatalogIds,
        String rawLine
) {
}
