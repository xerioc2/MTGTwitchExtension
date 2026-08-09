package com.mtgtwitch.extension.relay;

import com.mtgtwitch.extension.gamestate.GameState;
import com.mtgtwitch.extension.gamestate.GameStateFingerprint;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.client.MockServerRestTemplateCustomizer;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;

import java.time.Instant;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class SupabaseRelayPublisherTests {

    @Test
    void publishesThroughRelayFunctionWhenConfigured() {
        MockServerRestTemplateCustomizer customizer = new MockServerRestTemplateCustomizer();
        SupabaseRelayPublisher publisher = new SupabaseRelayPublisher(
                new RestTemplateBuilder(customizer),
                "xerioc2",
                "https://example.supabase.co/functions/v1/publish-game-state",
                "bridge-token"
        );
        MockRestServiceServer server = customizer.getServer();

        server.expect(once(), requestTo("https://example.supabase.co/functions/v1/publish-game-state"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer bridge-token"))
                .andExpect(content().string(containsString("\"channelId\":\"xerioc2\"")))
                .andExpect(content().string(containsString("\"gameState\"")))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        try {
            publisher.publish(emptyGameState());
            server.verify();
        } finally {
            publisher.close();
        }
    }

    @Test
    void doesNotPostWhenRelayFunctionIsConfiguredWithoutBridgePublishToken() {
        MockServerRestTemplateCustomizer customizer = new MockServerRestTemplateCustomizer();
        SupabaseRelayPublisher publisher = new SupabaseRelayPublisher(
                new RestTemplateBuilder(customizer),
                "xerioc2",
                "https://example.supabase.co/functions/v1/publish-game-state",
                ""
        );
        MockRestServiceServer server = customizer.getServer();

        try {
            publisher.publish(emptyGameState());
            server.verify();
        } finally {
            publisher.close();
        }
    }

    @Test
    void alwaysUsesConfiguredRelayFunctionEndpoint() {
        MockServerRestTemplateCustomizer customizer = new MockServerRestTemplateCustomizer();
        SupabaseRelayPublisher publisher = new SupabaseRelayPublisher(
                new RestTemplateBuilder(customizer),
                "xerioc2",
                "https://example.supabase.co/functions/v1/publish-game-state",
                "bridge-token"
        );
        MockRestServiceServer server = customizer.getServer();

        server.expect(once(), requestTo("https://example.supabase.co/functions/v1/publish-game-state"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        try {
            publisher.publish(emptyGameState());
            server.verify();
        } finally {
            publisher.close();
        }
    }

    @Test
    void doesNotPublishWhenRelayFunctionIsBlank() {
        MockServerRestTemplateCustomizer customizer = new MockServerRestTemplateCustomizer();
        SupabaseRelayPublisher publisher = new SupabaseRelayPublisher(
                new RestTemplateBuilder(customizer),
                "xerioc2",
                "",
                ""
        );
        MockRestServiceServer server = customizer.getServer();

        try {
            publisher.publish(emptyGameState());
            server.verify();
        } finally {
            publisher.close();
        }
    }

    @Test
    void coalescesRapidChangesAndPublishesTheLatestState() {
        MockServerRestTemplateCustomizer customizer = new MockServerRestTemplateCustomizer();
        SupabaseRelayPublisher publisher = new SupabaseRelayPublisher(
                new RestTemplateBuilder(customizer),
                "xerioc2",
                "https://example.supabase.co/functions/v1/publish-game-state",
                "bridge-token",
                java.time.Duration.ofMillis(75)
        );
        MockRestServiceServer server = customizer.getServer();

        server.expect(once(), requestTo("https://example.supabase.co/functions/v1/publish-game-state"))
                .andExpect(content().string(containsString("\"battlefield\":[]")))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo("https://example.supabase.co/functions/v1/publish-game-state"))
                .andExpect(content().string(containsString("\"battlefield\":[\"Counterspell\"]")))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        try {
            publisher.publish(gameStateWithBattlefield(List.of()));
            publisher.publish(gameStateWithBattlefield(List.of("Lightning Bolt")));
            publisher.publish(gameStateWithBattlefield(List.of("Counterspell")));

            server.verify(java.time.Duration.ofSeconds(2));
            assertEquals(3, publisher.metricsSnapshot().observedUpdates());
            assertEquals(1, publisher.metricsSnapshot().coalescedUpdates());
            assertEquals(2, publisher.metricsSnapshot().successfulPublishes());
        } finally {
            publisher.close();
        }
    }

    @Test
    void skipsTimestampOnlyChangesButKeepsAnIndependentHeartbeat() {
        MockServerRestTemplateCustomizer customizer = new MockServerRestTemplateCustomizer();
        SupabaseRelayPublisher publisher = new SupabaseRelayPublisher(
                new RestTemplateBuilder(customizer),
                "xerioc2",
                "https://example.supabase.co/functions/v1/publish-game-state",
                "bridge-token",
                java.time.Duration.ZERO,
                java.time.Duration.ofMillis(50)
        );
        MockRestServiceServer server = customizer.getServer();

        server.expect(org.springframework.test.web.client.ExpectedCount.times(2),
                        requestTo("https://example.supabase.co/functions/v1/publish-game-state"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        try {
            GameState firstState = gameStateWithBattlefield(
                    List.of("Lightning Bolt"),
                    Instant.parse("2026-05-31T00:00:00Z")
            );
            GameState timestampOnlyChange = gameStateWithBattlefield(
                    List.of("Lightning Bolt"),
                    Instant.parse("2026-05-31T00:00:01Z")
            );
            publisher.publish(firstState);
            publisher.publish(timestampOnlyChange);

            server.verify(java.time.Duration.ofSeconds(2));
            assertEquals(2, publisher.metricsSnapshot().observedUpdates());
            assertEquals(1, publisher.metricsSnapshot().deduplicatedUpdates());
            assertEquals(1, publisher.metricsSnapshot().heartbeatPublishes());
        } finally {
            publisher.close();
        }
    }

    @Test
    void fingerprintTracksEveryGameStateFieldExceptUpdatedAt() {
        GameState firstState = gameStateWithBattlefield(
                List.of("Lightning Bolt"),
                Instant.parse("2026-05-31T00:00:00Z")
        );
        GameState timestampOnlyChange = gameStateWithBattlefield(
                List.of("Lightning Bolt"),
                Instant.parse("2026-05-31T00:00:01Z")
        );

        assertEquals(
                GameState.class.getRecordComponents().length - 1,
                GameStateFingerprint.from(firstState).values().size()
        );
        assertEquals(
                GameStateFingerprint.from(firstState),
                GameStateFingerprint.from(timestampOnlyChange)
        );
    }

    private GameState emptyGameState() {
        return gameStateWithBattlefield(List.of());
    }

    private GameState gameStateWithBattlefield(List<String> battlefield) {
        return gameStateWithBattlefield(battlefield, Instant.parse("2026-05-31T00:00:00Z"));
    }

    private GameState gameStateWithBattlefield(List<String> battlefield, Instant updatedAt) {
        return new GameState(
                List.of(),
                battlefield,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                null,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                updatedAt
        );
    }
}
