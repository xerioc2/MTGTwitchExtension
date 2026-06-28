package com.mtgtwitch.extension.gamestate;

import java.util.List;

public record DeckCatalogEvent(
        long gameId,
        String username,
        List<DeckCard> deckCards,
        String rawLine
) {
}
