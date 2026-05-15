package com.mtgtwitch.extension.websocket;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final GameStateWebSocketHandler gameStateWebSocketHandler;

    public WebSocketConfig(GameStateWebSocketHandler gameStateWebSocketHandler) {
        this.gameStateWebSocketHandler = gameStateWebSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(gameStateWebSocketHandler, "/ws/game-state")
                .setAllowedOrigins("*");
    }
}
