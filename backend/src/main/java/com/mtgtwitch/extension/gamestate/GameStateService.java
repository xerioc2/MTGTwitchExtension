package com.mtgtwitch.extension.gamestate;

import com.mtgtwitch.extension.websocket.GameStateBroadcaster;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Service
public class GameStateService {

    private final Map<Zone, List<String>> zones = new EnumMap<>(Zone.class);
    private final GameStateBroadcaster gameStateBroadcaster;

    public GameStateService(GameStateBroadcaster gameStateBroadcaster) {
        this.gameStateBroadcaster = gameStateBroadcaster;

        for (Zone zone : Zone.values()) {
            zones.put(zone, new ArrayList<>());
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

    public synchronized GameState snapshot() {
        return new GameState(
                List.copyOf(zones.get(Zone.HAND)),
                List.copyOf(zones.get(Zone.BATTLEFIELD)),
                List.copyOf(zones.get(Zone.GRAVEYARD)),
                List.copyOf(zones.get(Zone.EXILE)),
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
}
