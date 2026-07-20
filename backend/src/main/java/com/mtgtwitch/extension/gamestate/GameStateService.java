package com.mtgtwitch.extension.gamestate;

import com.mtgtwitch.extension.detection.DetectionRegion;
import com.mtgtwitch.extension.websocket.GameStateBroadcaster;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalInt;

@Service
public class GameStateService {

    private static final Logger log = LoggerFactory.getLogger(GameStateService.class);
    private static final int MAX_CACHED_GAMES = 4;
    private static final Duration STALE_GAME_TTL = Duration.ofMinutes(30);

    private final Map<Long, PerGameState> games = new LinkedHashMap<>();
    private final GameStateBroadcaster gameStateBroadcaster;
    private Long activeGameId;
    private Long focusedGameId;
    private String localPlayerNameHint;
    private int replayDepth;
    private boolean replayBroadcastPending;

    public GameStateService(GameStateBroadcaster gameStateBroadcaster) {
        this.gameStateBroadcaster = gameStateBroadcaster;
    }

    public synchronized GameState apply(CardZoneEvent event) {
        PerGameState state = activeState();
        state.markUpdated(Instant.now());

        if (event.sourceZone() != null) {
            state.zones.get(event.sourceZone()).remove(event.cardName());
        } else {
            removeFromTrackedZones(state, event.cardName(), event.destinationZone());
        }

        if (!state.zones.get(event.destinationZone()).contains(event.cardName())) {
            state.zones.get(event.destinationZone()).add(event.cardName());
        }

        evictOldGames(Instant.now());

        GameState gameState = snapshot();
        broadcastOrDefer(gameState);
        return gameState;
    }

    public synchronized GameState apply(GameStatusEvent event) {
        PerGameState state = stateForStatusEvent(event);
        state.gameId = event.gameId();
        state.matchId = event.matchId();
        state.players = List.copyOf(event.players());
        state.markUpdated(Instant.now());

        evictFinishedGameFromSameMatch(state);
        activateGameForEvent(event.gameId());
        clearTrackedZones(state);

        int localPlayerId = resolveLocalPlayerId(state);
        event.cards().stream()
                .filter(card -> card.owner() == localPlayerId)
                .forEach(card -> addStatusCard(state, card));

        clearOpponentPublicZones(state);
        event.cards().stream()
                .filter(card -> card.owner() != localPlayerId)
                .forEach(card -> addOpponentStatusCard(state, card));

        evictOldGames(Instant.now());

        GameState gameState = snapshot();
        broadcastOrDefer(gameState);
        return gameState;
    }

    public synchronized GameState updateDeckCatalogIds(DeckCatalogEvent event) {
        PerGameState state = stateForGame(event.gameId());
        state.gameId = event.gameId();
        state.localPlayerName = event.username();
        localPlayerNameHint = normalizePlayerName(event.username());
        state.deckCards = List.copyOf(event.deckCards());
        state.deckCatalogIds = state.deckCards.stream()
                .map(DeckCard::catalogId)
                .distinct()
                .toList();
        state.markUpdated(Instant.now());

        int mainDeckCount = state.deckCards.stream()
                .filter(card -> !card.inSideboard())
                .mapToInt(DeckCard::quantity)
                .sum();
        int sideboardCount = state.deckCards.stream()
                .filter(DeckCard::inSideboard)
                .mapToInt(DeckCard::quantity)
                .sum();
        log.info(
                "Deck refreshed: gameId={}, main={}, sideboard={}, distinct={}",
                state.gameId,
                mainDeckCount,
                sideboardCount,
                state.deckCatalogIds.size()
        );

        activateGameForEvent(event.gameId());
        evictOldGames(Instant.now());

        GameState gameState = snapshot();
        broadcastOrDefer(gameState);
        return gameState;
    }

    public synchronized void recordLocalPlayerNameHint(String username) {
        String normalizedUsername = normalizePlayerName(username);
        if (normalizedUsername == null) {
            return;
        }

        localPlayerNameHint = normalizedUsername;
        log.info("MTGO local player hint updated from active log: {}", normalizedUsername);
    }

    public synchronized GameState focusGame(long gameId) {
        focusedGameId = gameId;
        if (!games.containsKey(gameId)) {
            log.debug("Recorded MTGO focused game {} before game state was cached.", gameId);
            return snapshot();
        }

        activeGameId = gameId;
        GameState gameState = snapshot();
        broadcastOrDefer(gameState);
        return gameState;
    }

    public synchronized GameState updateDetectionRegions(List<DetectionRegion> nextDetectionRegions) {
        PerGameState state = activeState();
        pruneExpiredDetectionRegions(state, Instant.now());
        state.detectionRegions = List.copyOf(nextDetectionRegions);
        state.markUpdated(Instant.now());

        evictOldGames(Instant.now());

        GameState gameState = snapshot();
        broadcastOrDefer(gameState);
        return gameState;
    }

    public synchronized void beginReplay() {
        replayDepth++;
    }

    public synchronized GameState endReplay() {
        if (replayDepth == 0) {
            return snapshot();
        }

        replayDepth--;
        GameState gameState = snapshot();
        if (replayDepth == 0 && replayBroadcastPending) {
            replayBroadcastPending = false;
            gameStateBroadcaster.broadcast(gameState);
        }
        return gameState;
    }

    public synchronized GameState snapshot() {
        PerGameState state = activeState();
        Instant now = Instant.now();
        pruneExpiredDetectionRegions(state, now);
        evictOldGames(now);

        return new GameState(
                List.copyOf(state.zones.get(Zone.HAND)),
                List.copyOf(state.zones.get(Zone.BATTLEFIELD)),
                List.copyOf(state.zones.get(Zone.GRAVEYARD)),
                List.copyOf(state.zones.get(Zone.EXILE)),
                List.copyOf(state.zoneCards.get(Zone.HAND)),
                List.copyOf(state.zoneCards.get(Zone.BATTLEFIELD)),
                List.copyOf(state.zoneCards.get(Zone.GRAVEYARD)),
                List.copyOf(state.zoneCards.get(Zone.EXILE)),
                List.copyOf(state.players),
                state.gameId,
                List.copyOf(state.deckCatalogIds),
                List.copyOf(state.deckCards),
                List.copyOf(state.detectionRegions),
                List.copyOf(state.opponentZoneCards.get(Zone.BATTLEFIELD)),
                List.copyOf(state.opponentZoneCards.get(Zone.GRAVEYARD)),
                List.copyOf(state.opponentZoneCards.get(Zone.EXILE)),
                now
        );
    }

    private PerGameState activeState() {
        ensureActiveGamePresent();
        return stateForGame(activeGameId);
    }

    private PerGameState stateForGame(Long gameId) {
        return games.computeIfAbsent(gameId, PerGameState::new);
    }

    private PerGameState stateForStatusEvent(GameStatusEvent event) {
        PerGameState state = games.get(event.gameId());
        if (state != null) {
            if ((state.localPlayerName == null || state.localPlayerName.isBlank()) && localPlayerNameHint != null) {
                state.localPlayerName = localPlayerNameHint;
            }
            return state;
        }

        PerGameState eventDeckState = games.remove(event.eventId());
        state = stateForGame(event.gameId());
        if (eventDeckState != null) {
            state.localPlayerName = eventDeckState.localPlayerName;
            state.deckCards = eventDeckState.deckCards;
            state.deckCatalogIds = eventDeckState.deckCatalogIds;
            state.detectionRegions = eventDeckState.detectionRegions;
        }
        if (state.localPlayerName == null || state.localPlayerName.isBlank()) {
            state.localPlayerName = localPlayerNameHint;
        }

        return state;
    }

    private void evictFinishedGameFromSameMatch(PerGameState nextState) {
        if (nextState.gameId == null || nextState.matchId == null) {
            return;
        }

        games.entrySet().stream()
                .filter(entry -> {
                    PerGameState state = entry.getValue();
                    return state.gameId != null
                            && !state.gameId.equals(nextState.gameId)
                            && nextState.matchId.equals(state.matchId);
                })
                .map(Map.Entry::getKey)
                .toList()
                .forEach(this::removeCachedGame);
    }

    private void evictOldGames(Instant now) {
        games.entrySet().stream()
                .filter(entry -> !isProtectedGame(entry.getKey()))
                .filter(entry -> entry.getValue().lastUpdatedAt.plus(STALE_GAME_TTL).isBefore(now))
                .map(Map.Entry::getKey)
                .toList()
                .forEach(this::removeCachedGame);

        while (games.size() > MAX_CACHED_GAMES) {
            Long oldestGameId = games.entrySet().stream()
                    .filter(entry -> !isProtectedGame(entry.getKey()))
                    .min(Comparator.comparing(entry -> entry.getValue().lastUpdatedAt))
                    .map(Map.Entry::getKey)
                    .orElse(null);

            if (oldestGameId == null) {
                return;
            }

            removeCachedGame(oldestGameId);
        }

        ensureActiveGamePresent();
    }

    private void activateGameForEvent(Long gameId) {
        if (Objects.equals(focusedGameId, gameId)) {
            activeGameId = gameId;
            return;
        }

        if (focusedGameId == null) {
            activeGameId = gameId;
        }
    }

    private boolean isProtectedGame(Long gameId) {
        return Objects.equals(gameId, activeGameId) || Objects.equals(gameId, focusedGameId);
    }

    private void removeCachedGame(Long gameId) {
        games.remove(gameId);
        if (Objects.equals(activeGameId, gameId)) {
            activeGameId = null;
        }
        if (Objects.equals(focusedGameId, gameId)) {
            focusedGameId = null;
        }
    }

    private void ensureActiveGamePresent() {
        if (activeGameId != null && games.containsKey(activeGameId)) {
            return;
        }

        if (focusedGameId != null) {
            if (games.containsKey(focusedGameId)) {
                activeGameId = focusedGameId;
            }
            return;
        }

        activeGameId = games.entrySet().stream()
                .max(Comparator.comparing(entry -> entry.getValue().lastUpdatedAt))
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    private void removeFromTrackedZones(PerGameState state, String cardName, Zone destinationZone) {
        for (Map.Entry<Zone, List<String>> entry : state.zones.entrySet()) {
            if (entry.getKey() != destinationZone) {
                entry.getValue().remove(cardName);
            }
        }
    }

    private void clearTrackedZones(PerGameState state) {
        for (Zone zone : Zone.values()) {
            state.zones.get(zone).clear();
            state.zoneCards.get(zone).clear();
        }
        clearOpponentPublicZones(state);
    }

    private void clearOpponentPublicZones(PerGameState state) {
        state.opponentZoneCards.get(Zone.BATTLEFIELD).clear();
        state.opponentZoneCards.get(Zone.GRAVEYARD).clear();
        state.opponentZoneCards.get(Zone.EXILE).clear();
    }

    private int resolveLocalPlayerId(PerGameState state) {
        OptionalInt playerIdByName = state.players.stream()
                .filter(player -> namesMatch(player.name(), state.localPlayerName))
                .mapToInt(PlayerState::id)
                .findFirst();

        if (playerIdByName.isPresent()) {
            return playerIdByName.getAsInt();
        }

        if (state.localPlayerName != null && !state.localPlayerName.isBlank()) {
            log.warn("Local player name '{}' did not match any player — falling back to first player.", state.localPlayerName);
        }

        return state.players.isEmpty() ? 0 : state.players.getFirst().id();
    }

    private boolean namesMatch(String playerName, String expectedName) {
        if (playerName == null || expectedName == null || expectedName.isBlank()) {
            return false;
        }

        return playerName.toLowerCase(Locale.ROOT).equals(expectedName.toLowerCase(Locale.ROOT));
    }

    private String normalizePlayerName(String username) {
        if (username == null || username.isBlank()) {
            return null;
        }

        return username.trim();
    }

    private void addStatusCard(PerGameState state, GameCard card) {
        if (card.catalogId() <= 0) {
            return;
        }

        Zone.fromLogText(card.actualZone())
                .filter(zone -> zone != Zone.EXILE || card.zone().equalsIgnoreCase("Exile"))
                .ifPresent(zone -> {
                    state.zoneCards.get(zone).add(card);
                    state.zones.get(zone).add(card.displayName());
                });
    }

    private void addOpponentStatusCard(PerGameState state, GameCard card) {
        if (card.catalogId() <= 0) {
            return;
        }

        Zone.fromLogText(card.actualZone())
                .filter(zone -> zone == Zone.BATTLEFIELD || zone == Zone.GRAVEYARD || zone == Zone.EXILE)
                .filter(zone -> zone != Zone.EXILE || card.zone().equalsIgnoreCase("Exile"))
                .ifPresent(zone -> state.opponentZoneCards.get(zone).add(card));
    }

    private void pruneExpiredDetectionRegions(PerGameState state, Instant now) {
        state.detectionRegions = state.detectionRegions.stream()
                .filter(region -> region.expiresAt() != null && region.expiresAt().isAfter(now))
                .toList();
    }

    private void broadcastOrDefer(GameState gameState) {
        if (replayDepth > 0) {
            replayBroadcastPending = true;
            return;
        }

        gameStateBroadcaster.broadcast(gameState);
    }

    private static class PerGameState {

        private final Map<Zone, List<String>> zones = new EnumMap<>(Zone.class);
        private final Map<Zone, List<GameCard>> zoneCards = new EnumMap<>(Zone.class);
        private final Map<Zone, List<GameCard>> opponentZoneCards = new EnumMap<>(Zone.class);
        private Long gameId;
        private Long matchId;
        private List<Integer> deckCatalogIds = List.of();
        private List<DeckCard> deckCards = List.of();
        private List<DetectionRegion> detectionRegions = List.of();
        private List<PlayerState> players = List.of();
        private String localPlayerName;
        private Instant lastUpdatedAt = Instant.now();

        private PerGameState(Long gameId) {
            this.gameId = gameId;

            for (Zone zone : Zone.values()) {
                zones.put(zone, new ArrayList<>());
                zoneCards.put(zone, new ArrayList<>());
            }

            opponentZoneCards.put(Zone.BATTLEFIELD, new ArrayList<>());
            opponentZoneCards.put(Zone.GRAVEYARD, new ArrayList<>());
            opponentZoneCards.put(Zone.EXILE, new ArrayList<>());
        }

        private void markUpdated(Instant now) {
            lastUpdatedAt = now;
        }
    }
}
