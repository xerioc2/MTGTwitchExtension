package com.mtgtwitch.extension.detection.detector;

import com.mtgtwitch.extension.detection.DetectionRegion;
import com.mtgtwitch.extension.detection.DetectionRegionService;
import com.mtgtwitch.extension.gamestate.GameState;
import com.mtgtwitch.extension.gamestate.GameStateService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
public class ScreenDetectorPublisher {

    private final DetectionRegionService detectionRegionService;
    private final GameStateService gameStateService;
    private final MockScreenCardDetector mockDetector;
    private final ManualLayoutScreenCardDetector manualDetector;
    private final boolean detectionsEnabled;
    private final boolean detectorEnabled;
    private final DetectorMode detectorMode;
    private final Duration minimumInterval;
    private final String defaultChannelId;
    private final Clock clock;
    private Instant lastPublishedAt;

    @Autowired
    public ScreenDetectorPublisher(
            DetectionRegionService detectionRegionService,
            GameStateService gameStateService,
            MockScreenCardDetector mockDetector,
            ManualLayoutScreenCardDetector manualDetector,
            @Value("${screen-detections.enabled:false}") boolean detectionsEnabled,
            @Value("${screen-detections.detector.enabled:false}") boolean detectorEnabled,
            @Value("${screen-detections.detector.mode:none}") String detectorMode,
            @Value("${screen-detections.detector.min-interval:PT1S}") Duration minimumInterval,
            @Value("${supabase.channel-id:local}") String defaultChannelId
    ) {
        this(
                detectionRegionService,
                gameStateService,
                mockDetector,
                manualDetector,
                detectionsEnabled,
                detectorEnabled,
                DetectorMode.fromConfig(detectorMode),
                minimumInterval,
                defaultChannelId,
                Clock.systemUTC()
        );
    }

    ScreenDetectorPublisher(
            DetectionRegionService detectionRegionService,
            GameStateService gameStateService,
            MockScreenCardDetector mockDetector,
            ManualLayoutScreenCardDetector manualDetector,
            boolean detectionsEnabled,
            boolean detectorEnabled,
            DetectorMode detectorMode,
            Duration minimumInterval,
            String defaultChannelId,
            Clock clock
    ) {
        this.detectionRegionService = detectionRegionService;
        this.gameStateService = gameStateService;
        this.mockDetector = mockDetector;
        this.manualDetector = manualDetector;
        this.detectionsEnabled = detectionsEnabled;
        this.detectorEnabled = detectorEnabled;
        this.detectorMode = detectorMode;
        this.minimumInterval = minimumInterval;
        this.defaultChannelId = defaultChannelId;
        this.clock = clock;
    }

    public synchronized DetectorPublishResult publishOnce(String channelId) {
        GameState currentState = gameStateService.snapshot();
        if (!detectionsEnabled || !detectorEnabled || detectorMode == DetectorMode.NONE) {
            return DetectorPublishResult.skipped("disabled", currentState);
        }

        Instant now = Instant.now(clock);
        if (lastPublishedAt != null && now.isBefore(lastPublishedAt.plus(minimumInterval))) {
            return DetectorPublishResult.skipped("throttled", currentState);
        }

        DetectorContext context = new DetectorContext(
                channelId == null || channelId.isBlank() ? defaultChannelId : channelId,
                currentState.gameId(),
                null,
                null,
                now,
                detectorMode.name(),
                Map.of()
        );
        List<DetectionRegion> regions = detectorForMode().detect(currentState, context);
        GameState nextState = detectionRegionService.update(context.channelId(), regions);
        lastPublishedAt = now;

        return DetectorPublishResult.published(regions.size(), nextState);
    }

    private ScreenCardDetector detectorForMode() {
        return detectorMode == DetectorMode.MANUAL ? manualDetector : mockDetector;
    }
}
