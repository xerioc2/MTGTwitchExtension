package com.mtgtwitch.extension.api;

import com.mtgtwitch.extension.detection.DetectionRegionRequest;
import com.mtgtwitch.extension.detection.DetectionRegionService;
import com.mtgtwitch.extension.detection.MockDetectionProvider;
import com.mtgtwitch.extension.detection.detector.DetectorPublishResult;
import com.mtgtwitch.extension.detection.detector.ScreenDetectorPublisher;
import com.mtgtwitch.extension.gamestate.GameState;
import com.mtgtwitch.extension.gamestate.GameStateService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/detection-regions")
@ConditionalOnProperty(name = "screen-detections.enabled", havingValue = "true")
public class DetectionRegionController {

    private final DetectionRegionService detectionRegionService;
    private final GameStateService gameStateService;
    private final MockDetectionProvider mockDetectionProvider;
    private final ScreenDetectorPublisher screenDetectorPublisher;
    private final String defaultChannelId;
    private final boolean mockEnabled;

    public DetectionRegionController(
            DetectionRegionService detectionRegionService,
            GameStateService gameStateService,
            MockDetectionProvider mockDetectionProvider,
            ScreenDetectorPublisher screenDetectorPublisher,
            @Value("${supabase.channel-id:local}") String defaultChannelId,
            @Value("${screen-detections.mock-enabled:false}") boolean mockEnabled
    ) {
        this.detectionRegionService = detectionRegionService;
        this.gameStateService = gameStateService;
        this.mockDetectionProvider = mockDetectionProvider;
        this.screenDetectorPublisher = screenDetectorPublisher;
        this.defaultChannelId = defaultChannelId;
        this.mockEnabled = mockEnabled;
    }

    @PutMapping
    public ResponseEntity<GameState> update(@RequestBody DetectionRegionRequest request) {
        return ResponseEntity.ok(detectionRegionService.update(request.channelId(), request.regions()));
    }

    @PostMapping("/mock")
    public ResponseEntity<GameState> publishMock(@RequestParam(required = false) String channelId) {
        if (!mockEnabled) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        String resolvedChannelId = channelId == null || channelId.isBlank() ? defaultChannelId : channelId;
        GameState currentState = gameStateService.snapshot();
        return ResponseEntity.ok(detectionRegionService.update(
                resolvedChannelId,
                mockDetectionProvider.generate(resolvedChannelId, currentState)
        ));
    }

    @PostMapping("/detector/run")
    public ResponseEntity<DetectorPublishResult> runDetector(@RequestParam(required = false) String channelId) {
        DetectorPublishResult result = screenDetectorPublisher.publishOnce(channelId);
        if (!result.published() && "disabled".equals(result.status())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(result);
        }

        return ResponseEntity.ok(result);
    }
}
