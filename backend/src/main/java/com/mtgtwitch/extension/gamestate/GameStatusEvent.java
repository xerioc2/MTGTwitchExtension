package com.mtgtwitch.extension.gamestate;

import java.util.List;

public record GameStatusEvent(
        long gameId,
        long matchId,
        long eventId,
        List<PlayerState> players,
        List<GameCard> cards,
        String rawLine
) {
}
