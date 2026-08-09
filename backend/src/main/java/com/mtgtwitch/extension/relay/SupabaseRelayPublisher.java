package com.mtgtwitch.extension.relay;

import com.mtgtwitch.extension.gamestate.GameState;
import com.mtgtwitch.extension.gamestate.GameStateFingerprint;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Service
public class SupabaseRelayPublisher {

    private static final Logger log = LoggerFactory.getLogger(SupabaseRelayPublisher.class);
    private final RestTemplate restTemplate;
    private final long minPublishIntervalNanos;
    private final long publishHeartbeatIntervalNanos;
    private final ScheduledExecutorService publishScheduler;
    private final ScheduledFuture<?> metricsLogTask;
    private String relayFunctionUrl;
    private String bridgePublishToken;
    private String channelId;
    private GameStateFingerprint lastPublishedContent;
    private GameState lastPublishedGameState;
    private GameState pendingGameState;
    private GameStateFingerprint pendingContent;
    private ScheduledFuture<?> pendingPublishTask;
    private ScheduledFuture<?> heartbeatTask;
    private long lastPublishStartedAtNanos;
    private long lastPublishedAtNanos;
    private long lastObservedAtNanos;
    private long observedUpdates;
    private long deduplicatedUpdates;
    private long coalescedUpdates;
    private long successfulPublishes;
    private long failedPublishes;
    private long heartbeatPublishes;

    public SupabaseRelayPublisher(
            RestTemplateBuilder restTemplateBuilder,
            String channelId,
            String relayFunctionUrl,
            String bridgePublishToken
    ) {
        this(
                restTemplateBuilder,
                channelId,
                relayFunctionUrl,
                bridgePublishToken,
                Duration.ofSeconds(1),
                Duration.ofSeconds(15),
                Duration.ofMinutes(5)
        );
    }

    public SupabaseRelayPublisher(
            RestTemplateBuilder restTemplateBuilder,
            String channelId,
            String relayFunctionUrl,
            String bridgePublishToken,
            Duration minPublishInterval
    ) {
        this(
                restTemplateBuilder,
                channelId,
                relayFunctionUrl,
                bridgePublishToken,
                minPublishInterval,
                Duration.ofSeconds(15),
                Duration.ofMinutes(5)
        );
    }

    public SupabaseRelayPublisher(
            RestTemplateBuilder restTemplateBuilder,
            String channelId,
            String relayFunctionUrl,
            String bridgePublishToken,
            Duration minPublishInterval,
            Duration publishHeartbeatInterval
    ) {
        this(
                restTemplateBuilder,
                channelId,
                relayFunctionUrl,
                bridgePublishToken,
                minPublishInterval,
                publishHeartbeatInterval,
                Duration.ofMinutes(5)
        );
    }

    @Autowired
    public SupabaseRelayPublisher(
            RestTemplateBuilder restTemplateBuilder,
            @Value("${supabase.channel-id}") String channelId,
            @Value("${supabase.relay-function-url}") String relayFunctionUrl,
            @Value("${supabase.bridge-publish-token}") String bridgePublishToken,
            @Value("${supabase.publish-min-interval:PT1S}") Duration minPublishInterval,
            @Value("${supabase.publish-heartbeat-interval:PT15S}") Duration publishHeartbeatInterval,
            @Value("${supabase.metrics-log-interval:PT5M}") Duration metricsLogInterval
    ) {
        this.restTemplate = restTemplateBuilder
                .connectTimeout(Duration.ofSeconds(3))
                .readTimeout(Duration.ofSeconds(3))
                .build();
        this.relayFunctionUrl = relayFunctionUrl;
        this.bridgePublishToken = bridgePublishToken;
        this.channelId = channelId;
        this.minPublishIntervalNanos = Math.max(0, minPublishInterval.toNanos());
        this.publishHeartbeatIntervalNanos = Math.max(
                TimeUnit.MILLISECONDS.toNanos(10),
                Math.max(this.minPublishIntervalNanos, publishHeartbeatInterval.toNanos())
        );
        this.publishScheduler = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "supabase-relay-publisher");
            thread.setDaemon(true);
            return thread;
        });
        long metricsIntervalNanos = Math.max(TimeUnit.SECONDS.toNanos(1), metricsLogInterval.toNanos());
        this.metricsLogTask = publishScheduler.scheduleWithFixedDelay(
                this::logMetrics,
                metricsIntervalNanos,
                metricsIntervalNanos,
                TimeUnit.NANOSECONDS
        );
    }

    public synchronized void configure(String channelId, String relayFunctionUrl, String bridgePublishToken) {
        clearPendingPublish();
        clearHeartbeat();
        lastPublishedContent = null;
        lastPublishedGameState = null;
        lastPublishStartedAtNanos = 0;
        lastPublishedAtNanos = 0;
        lastObservedAtNanos = 0;
        this.channelId = channelId;
        this.relayFunctionUrl = relayFunctionUrl;
        this.bridgePublishToken = bridgePublishToken;
        log.info("Configured Supabase relay publisher for channel '{}'.", StringUtils.hasText(channelId) ? channelId : "none");
    }

    public synchronized void publish(GameState gameState) {
        if (!isPublishingConfigured()) {
            return;
        }

        observedUpdates += 1;
        long now = System.nanoTime();
        lastObservedAtNanos = now;
        GameStateFingerprint nextContent = GameStateFingerprint.from(gameState);
        if (nextContent.equals(lastPublishedContent)) {
            deduplicatedUpdates += 1;
            clearPendingPublish();
            if (heartbeatTask == null && now - lastPublishedAtNanos >= publishHeartbeatIntervalNanos) {
                publishNow(gameState, nextContent, true);
            }
            return;
        }
        if (nextContent.equals(pendingContent)) {
            deduplicatedUpdates += 1;
            return;
        }

        long elapsed = lastPublishStartedAtNanos == 0
                ? Long.MAX_VALUE
                : now - lastPublishStartedAtNanos;
        if (pendingPublishTask == null && elapsed >= minPublishIntervalNanos) {
            publishNow(gameState, nextContent, false);
            return;
        }

        if (pendingContent != null) {
            coalescedUpdates += 1;
        }
        pendingGameState = gameState;
        pendingContent = nextContent;
        if (pendingPublishTask == null) {
            long delayNanos = Math.max(0, minPublishIntervalNanos - elapsed);
            pendingPublishTask = publishScheduler.schedule(this::flushPendingPublish, delayNanos, TimeUnit.NANOSECONDS);
        }
    }

    @PreDestroy
    public synchronized void close() {
        clearPendingPublish();
        clearHeartbeat();
        metricsLogTask.cancel(false);
        logMetrics();
        publishScheduler.shutdownNow();
    }

    private boolean isPublishingConfigured() {
        return StringUtils.hasText(relayFunctionUrl);
    }

    private synchronized void flushPendingPublish() {
        pendingPublishTask = null;
        GameState nextGameState = pendingGameState;
        GameStateFingerprint nextContent = pendingContent;
        pendingGameState = null;
        pendingContent = null;

        if (nextGameState == null || nextContent == null || nextContent.equals(lastPublishedContent)) {
            return;
        }

        publishNow(nextGameState, nextContent, false);
    }

    private boolean publishNow(GameState gameState, GameStateFingerprint content, boolean heartbeat) {
        lastPublishStartedAtNanos = System.nanoTime();
        boolean published = publishThroughRelayFunction(gameState);

        if (published) {
            successfulPublishes += 1;
            if (heartbeat) {
                heartbeatPublishes += 1;
            }
            lastPublishedContent = content;
            lastPublishedGameState = gameState;
            lastPublishedAtNanos = System.nanoTime();
            scheduleHeartbeat();
        } else {
            failedPublishes += 1;
        }
        return published;
    }

    private synchronized void publishHeartbeat() {
        heartbeatTask = null;
        if (!isPublishingConfigured() || lastPublishedGameState == null || lastPublishedContent == null) {
            return;
        }
        if (System.nanoTime() - lastObservedAtNanos > publishHeartbeatIntervalNanos * 2) {
            return;
        }

        if (pendingPublishTask != null) {
            scheduleHeartbeat();
            return;
        }

        if (!publishNow(lastPublishedGameState, lastPublishedContent, true)) {
            scheduleHeartbeat();
        }
    }

    private void scheduleHeartbeat() {
        clearHeartbeat();
        if (!publishScheduler.isShutdown()) {
            heartbeatTask = publishScheduler.schedule(
                    this::publishHeartbeat,
                    publishHeartbeatIntervalNanos,
                    TimeUnit.NANOSECONDS
            );
        }
    }

    private boolean publishThroughRelayFunction(GameState gameState) {
        if (!StringUtils.hasText(bridgePublishToken)) {
            log.warn("Supabase relay function URL is configured, but BRIDGE_PUBLISH_TOKEN is missing.");
            return false;
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(bridgePublishToken);

        RelayFunctionRequest request = new RelayFunctionRequest(channelId, gameState);

        try {
            restTemplate.postForEntity(relayFunctionUrl, new HttpEntity<>(request, headers), Void.class);
            log.debug("Published MTGO game state through relay function channel game-state:{}.", channelId);
            return true;
        } catch (RestClientException exception) {
            log.warn("Failed to publish MTGO game state through relay function channel game-state:{}.", channelId, exception);
            return false;
        }
    }

    private void clearPendingPublish() {
        if (pendingPublishTask != null) {
            pendingPublishTask.cancel(false);
            pendingPublishTask = null;
        }
        pendingGameState = null;
        pendingContent = null;
    }

    private void clearHeartbeat() {
        if (heartbeatTask != null) {
            heartbeatTask.cancel(false);
            heartbeatTask = null;
        }
    }

    synchronized RelayMetrics metricsSnapshot() {
        return new RelayMetrics(
                observedUpdates,
                deduplicatedUpdates,
                coalescedUpdates,
                successfulPublishes,
                failedPublishes,
                heartbeatPublishes
        );
    }

    private synchronized void logMetrics() {
        RelayMetrics metrics = metricsSnapshot();
        if (metrics.observedUpdates() == 0) {
            return;
        }

        log.info(
                "Supabase relay metrics: observed={}, deduplicated={}, coalesced={}, successful={}, failed={}, heartbeats={}.",
                metrics.observedUpdates(),
                metrics.deduplicatedUpdates(),
                metrics.coalescedUpdates(),
                metrics.successfulPublishes(),
                metrics.failedPublishes(),
                metrics.heartbeatPublishes()
        );
    }

    record RelayMetrics(
            long observedUpdates,
            long deduplicatedUpdates,
            long coalescedUpdates,
            long successfulPublishes,
            long failedPublishes,
            long heartbeatPublishes
    ) {
    }

    private record RelayFunctionRequest(String channelId, GameState gameState) {
    }
}
