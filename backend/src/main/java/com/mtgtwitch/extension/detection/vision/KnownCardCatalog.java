package com.mtgtwitch.extension.detection.vision;

import com.mtgtwitch.extension.gamestate.GameCard;
import com.mtgtwitch.extension.gamestate.GameState;
import com.mtgtwitch.extension.scryfall.ScryfallCard;
import com.mtgtwitch.extension.scryfall.ScryfallService;
import com.mtgtwitch.extension.scryfall.ScryfallServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class KnownCardCatalog {

    private static final Logger log = LoggerFactory.getLogger(KnownCardCatalog.class);
    private final ScryfallService scryfallService;

    public KnownCardCatalog(ScryfallService scryfallService) {
        this.scryfallService = scryfallService;
    }

    public List<KnownGameCard> resolve(GameState gameState) {
        if (gameState == null) {
            return List.of();
        }

        List<CardInZone> cards = new ArrayList<>();
        add(cards, gameState.handCards(), "HAND");
        add(cards, gameState.battlefieldCards(), "BATTLEFIELD");
        add(cards, gameState.graveyardCards(), "GRAVEYARD");
        add(cards, gameState.exileCards(), "EXILE");
        add(cards, gameState.opponentBattlefieldCards(), "OPPONENT_BATTLEFIELD");
        add(cards, gameState.opponentGraveyardCards(), "OPPONENT_GRAVEYARD");
        add(cards, gameState.opponentExileCards(), "OPPONENT_EXILE");

        Map<Integer, CardInZone> distinctInstances = new LinkedHashMap<>();
        cards.stream()
                .filter(card -> card.card().catalogId() > 0)
                .forEach(card -> distinctInstances.putIfAbsent(card.card().id(), card));
        try {
            Map<Integer, ScryfallCard> details = scryfallService.fetchCards(
                    distinctInstances.values().stream().map(card -> card.card().catalogId()).distinct().toList()
            );
            return distinctInstances.values().stream()
                    .filter(card -> details.containsKey(card.card().catalogId()))
                    .map(card -> new KnownGameCard(
                            card.card(),
                            details.get(card.card().catalogId()),
                            card.zone()
                    ))
                    .toList();
        } catch (ScryfallServiceException exception) {
            log.debug("Known card art resolution skipped: {}", exception.getMessage());
            return List.of();
        }
    }

    private static void add(List<CardInZone> target, List<GameCard> cards, String zone) {
        if (cards != null) {
            cards.forEach(card -> target.add(new CardInZone(card, zone)));
        }
    }

    private record CardInZone(GameCard card, String zone) {
    }
}
