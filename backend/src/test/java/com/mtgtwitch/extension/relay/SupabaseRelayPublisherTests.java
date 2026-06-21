package com.mtgtwitch.extension.relay;

import com.mtgtwitch.extension.gamestate.GameState;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.client.MockServerRestTemplateCustomizer;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;

import java.time.Instant;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.ExpectedCount.never;
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
                "https://example.supabase.co",
                "",
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

        publisher.publish(emptyGameState());

        server.verify();
    }

    @Test
    void doesNotPostWhenRelayFunctionIsConfiguredWithoutBridgePublishToken() {
        MockServerRestTemplateCustomizer customizer = new MockServerRestTemplateCustomizer();
        SupabaseRelayPublisher publisher = new SupabaseRelayPublisher(
                new RestTemplateBuilder(customizer),
                "https://example.supabase.co",
                "",
                "xerioc2",
                "https://example.supabase.co/functions/v1/publish-game-state",
                ""
        );
        MockRestServiceServer server = customizer.getServer();

        publisher.publish(emptyGameState());

        server.verify();
    }

    @Test
    void doesNotUseDirectBroadcastWhenRelayFunctionIsConfigured() {
        MockServerRestTemplateCustomizer customizer = new MockServerRestTemplateCustomizer();
        SupabaseRelayPublisher publisher = new SupabaseRelayPublisher(
                new RestTemplateBuilder(customizer),
                "https://example.supabase.co",
                "service-role-key",
                "xerioc2",
                "https://example.supabase.co/functions/v1/publish-game-state",
                "bridge-token"
        );
        MockRestServiceServer server = customizer.getServer();

        server.expect(once(), requestTo("https://example.supabase.co/functions/v1/publish-game-state"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        server.expect(never(), requestTo("https://example.supabase.co/realtime/v1/api/broadcast"));

        publisher.publish(emptyGameState());

        server.verify();
    }

    @Test
    void fallsBackToDirectBroadcastWhenRelayFunctionIsBlankAndServiceRoleKeyIsSet() {
        MockServerRestTemplateCustomizer customizer = new MockServerRestTemplateCustomizer();
        SupabaseRelayPublisher publisher = new SupabaseRelayPublisher(
                new RestTemplateBuilder(customizer),
                "https://example.supabase.co",
                "service-role-key",
                "xerioc2",
                "",
                ""
        );
        MockRestServiceServer server = customizer.getServer();

        server.expect(once(), requestTo("https://example.supabase.co/realtime/v1/api/broadcast"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer service-role-key"))
                .andExpect(header("apikey", "service-role-key"))
                .andExpect(content().string(containsString("\"topic\":\"game-state:xerioc2\"")))
                .andExpect(content().string(containsString("\"event\":\"game-state\"")))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        publisher.publish(emptyGameState());

        server.verify();
    }

    private GameState emptyGameState() {
        return new GameState(
                List.of(),
                List.of(),
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
                Instant.parse("2026-05-31T00:00:00Z")
        );
    }
}
