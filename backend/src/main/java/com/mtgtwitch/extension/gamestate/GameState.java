package com.mtgtwitch.extension.gamestate;

import com.mtgtwitch.extension.detection.DetectionRegion;

import java.time.Instant;
import java.util.List;

public record GameState(
        List<String> hand,
        List<String> battlefield,
        List<String> graveyard,
        List<String> exile,
        List<GameCard> handCards,
        List<GameCard> battlefieldCards,
        List<GameCard> graveyardCards,
        List<GameCard> exileCards,
        List<PlayerState> players,
        Long gameId,
        List<Integer> deckCatalogIds,
        List<DeckCard> deckCards,
        List<DetectionRegion> detectionRegions,
        List<GameCard> opponentBattlefieldCards,
        List<GameCard> opponentGraveyardCards,
        List<GameCard> opponentExileCards,
        Instant updatedAt
) {
}
