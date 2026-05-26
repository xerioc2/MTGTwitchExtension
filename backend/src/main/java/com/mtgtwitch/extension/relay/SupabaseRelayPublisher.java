package com.mtgtwitch.extension.relay;

import com.mtgtwitch.extension.gamestate.GameState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.List;

@Service
public class SupabaseRelayPublisher {

    private static final Logger log = LoggerFactory.getLogger(SupabaseRelayPublisher.class);
    private static final String GAME_STATE_EVENT = "game-state";

    private final RestTemplate restTemplate;
    private final String broadcastUrl;
    private final String serviceRoleKey;
    private final String channelId;

    public SupabaseRelayPublisher(
            RestTemplateBuilder restTemplateBuilder,
            @Value("${supabase.url}") String supabaseUrl,
            @Value("${supabase.service-role-key}") String serviceRoleKey,
            @Value("${supabase.channel-id}") String channelId
    ) {
        this.restTemplate = restTemplateBuilder
                .setConnectTimeout(Duration.ofSeconds(3))
                .setReadTimeout(Duration.ofSeconds(3))
                .build();
        this.broadcastUrl = supabaseUrl.replaceAll("/+$", "") + "/realtime/v1/api/broadcast";
        this.serviceRoleKey = serviceRoleKey;
        this.channelId = channelId;
    }

    public void publish(GameState gameState) {
        if (!StringUtils.hasText(serviceRoleKey)) {
            return;
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("apikey", serviceRoleKey);
        headers.setBearerAuth(serviceRoleKey);

        BroadcastMessage message = new BroadcastMessage(
                "game-state:" + channelId,
                GAME_STATE_EVENT,
                gameState
        );
        BroadcastRequest request = new BroadcastRequest(List.of(message));

        try {
            restTemplate.postForEntity(broadcastUrl, new HttpEntity<>(request, headers), Void.class);
            log.info("Published MTGO game state to Supabase relay channel game-state:{}.", channelId);
        } catch (RestClientException exception) {
            log.warn("Failed to publish MTGO game state to Supabase relay channel game-state:{}.", channelId, exception);
        }
    }

    private record BroadcastRequest(List<BroadcastMessage> messages) {
    }

    private record BroadcastMessage(String topic, String event, GameState payload) {
    }
}
