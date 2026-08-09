package com.mtgtwitch.extension.detection.detector;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@EnableScheduling
@ConditionalOnProperty(name = "screen-detections.detector.auto-run", havingValue = "true")
public class ScreenDetectorScheduler {

    private static final Logger log = LoggerFactory.getLogger(ScreenDetectorScheduler.class);
    private final ScreenDetectorPublisher publisher;

    public ScreenDetectorScheduler(ScreenDetectorPublisher publisher) {
        this.publisher = publisher;
    }

    @Scheduled(
            initialDelayString = "${screen-detections.detector.scan-interval:PT5S}",
            fixedDelayString = "${screen-detections.detector.scan-interval:PT5S}"
    )
    public void scan() {
        try {
            publisher.publishOnce(null);
        } catch (RuntimeException exception) {
            log.debug("Scheduled screen detector scan skipped: {}", exception.getMessage());
        }
    }
}
