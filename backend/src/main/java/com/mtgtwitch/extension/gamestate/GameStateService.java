package com.mtgtwitch.extension.gamestate;

import com.mtgtwitch.extension.websocket.GameStateBroadcaster;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalInt;

@Service
public class GameStateService {

    private final Map<Zone, List<String>> zones = new EnumMap<>(Zone.class);
    private final Map<Zone, List<GameCard>> zoneCards = new EnumMap<>(Zone.class);
    private final GameStateBroadcaster gameStateBroadcaster;
    private Long gameId;
    private List<Integer> deckCatalogIds = List.of();
    private List<PlayerState> players = List.of();
    private String localPlayerName;

    public GameStateService(GameStateBroadcaster gameStateBroadcaster) {
        this.gameStateBroadcaster = gameStateBroadcaster;

        for (Zone zone : Zone.values()) {
            zones.put(zone, new ArrayList<>());
            zoneCards.put(zone, new ArrayList<>());
        }
    }

    public synchronized GameState apply(CardZoneEvent event) {
        if (event.sourceZone() != null) {
            zones.get(event.sourceZone()).remove(event.cardName());
        } else {
            removeFromTrackedZones(event.cardName(), event.destinationZone());
        }

        if (!zones.get(event.destinationZone()).contains(event.cardName())) {
            zones.get(event.destinationZone()).add(event.cardName());
        }

        GameState gameState = snapshot();
        gameStateBroadcaster.broadcast(gameState);
        return gameState;
    }

    public synchronized GameState apply(GameStatusEvent event) {
        resetForNewGame(event.gameId());
        gameId = event.gameId();
        players = List.copyOf(event.players());
        clearTrackedZones();

        int localPlayerId = resolveLocalPlayerId();
        event.cards().stream()
                .filter(card -> card.owner() == localPlayerId)
                .forEach(this::addStatusCard);

        GameState gameState = snapshot();
        gameStateBroadcaster.broadcast(gameState);
        return gameState;
    }

    public synchronized GameState updateDeckCatalogIds(DeckCatalogEvent event) {
        resetForNewGame(event.gameId());
        gameId = event.gameId();
        localPlayerName = event.username();
        deckCatalogIds = List.copyOf(event.deckCatalogIds());

        GameState gameState = snapshot();
        gameStateBroadcaster.broadcast(gameState);
        return gameState;
    }

    public synchronized GameState snapshot() {
        return new GameState(
                List.copyOf(zones.get(Zone.HAND)),
                List.copyOf(zones.get(Zone.BATTLEFIELD)),
                List.copyOf(zones.get(Zone.GRAVEYARD)),
                List.copyOf(zones.get(Zone.EXILE)),
                List.copyOf(zoneCards.get(Zone.HAND)),
                List.copyOf(zoneCards.get(Zone.BATTLEFIELD)),
                List.copyOf(zoneCards.get(Zone.GRAVEYARD)),
                List.copyOf(zoneCards.get(Zone.EXILE)),
                List.copyOf(players),
                gameId,
                List.copyOf(deckCatalogIds),
                Instant.now()
        );
    }

    private void removeFromTrackedZones(String cardName, Zone destinationZone) {
        for (Map.Entry<Zone, List<String>> entry : zones.entrySet()) {
            if (entry.getKey() != destinationZone) {
                entry.getValue().remove(cardName);
            }
        }
    }

    private void clearTrackedZones() {
        for (Zone zone : Zone.values()) {
            zones.get(zone).clear();
            zoneCards.get(zone).clear();
        }
    }

    private void resetForNewGame(long nextGameId) {
        if (gameId != null && gameId != nextGameId) {
            clearTrackedZones();
            deckCatalogIds = List.of();
            players = List.of();
        }
    }

    private int resolveLocalPlayerId() {
        OptionalInt playerIdByName = players.stream()
                .filter(player -> namesMatch(player.name(), localPlayerName))
                .mapToInt(PlayerState::id)
                .findFirst();

        if (playerIdByName.isPresent()) {
            return playerIdByName.getAsInt();
        }

        return players.isEmpty() ? 0 : players.getFirst().id();
    }

    private boolean namesMatch(String playerName, String expectedName) {
        if (playerName == null || expectedName == null || expectedName.isBlank()) {
            return false;
        }

        return playerName.toLowerCase(Locale.ROOT).equals(expectedName.toLowerCase(Locale.ROOT));
    }

    private void addStatusCard(GameCard card) {
        Zone.fromLogText(card.actualZone())
                .filter(zone -> zone != Zone.EXILE || card.zone().equalsIgnoreCase("Exile"))
                .ifPresent(zone -> {
                    zoneCards.get(zone).add(card);
                    zones.get(zone).add(card.displayName());
                });
    }
}
