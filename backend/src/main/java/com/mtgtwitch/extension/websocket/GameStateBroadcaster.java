package com.mtgtwitch.extension.websocket;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mtgtwitch.extension.gamestate.GameState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

@Component
public class GameStateBroadcaster {

    private static final Logger log = LoggerFactory.getLogger(GameStateBroadcaster.class);

    private final Set<WebSocketSession> sessions = new CopyOnWriteArraySet<>();
    private final ObjectMapper objectMapper;
    private volatile String latestGameStateJson;

    public GameStateBroadcaster(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void addSession(WebSocketSession session) {
        sessions.add(session);
        log.info("Game state WebSocket client connected: {}", session.getId());

        if (latestGameStateJson != null) {
            send(session, latestGameStateJson);
        }
    }

    public void removeSession(WebSocketSession session) {
        sessions.remove(session);
        log.info("Game state WebSocket client disconnected: {}", session.getId());
    }

    public void broadcast(GameState gameState) {
        try {
            latestGameStateJson = objectMapper.writeValueAsString(gameState);
        } catch (JsonProcessingException exception) {
            log.warn("Failed to serialize MTGO game state.", exception);
            return;
        }

        for (WebSocketSession session : sessions) {
            send(session, latestGameStateJson);
        }
    }

    private void send(WebSocketSession session, String message) {
        if (!session.isOpen()) {
            sessions.remove(session);
            return;
        }

        try {
            session.sendMessage(new TextMessage(message));
        } catch (IOException exception) {
            sessions.remove(session);
            log.warn("Failed to send MTGO game state to WebSocket client {}.", session.getId(), exception);
        }
    }
}
