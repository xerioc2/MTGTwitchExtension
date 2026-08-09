package com.mtgtwitch.extension.detection.vision;

import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class FrameSourceRouter implements FrameSource {

    private final LocalVisionDetectorProperties properties;
    private final ScreenshotFrameSource screenshotFrameSource;
    private final ObsWebSocketFrameSource obsFrameSource;

    public FrameSourceRouter(
            LocalVisionDetectorProperties properties,
            ScreenshotFrameSource screenshotFrameSource,
            ObsWebSocketFrameSource obsFrameSource
    ) {
        this.properties = properties;
        this.screenshotFrameSource = screenshotFrameSource;
        this.obsFrameSource = obsFrameSource;
    }

    @Override
    public Optional<CapturedFrame> capture() {
        return switch (properties.captureMode()) {
            case SCREENSHOT -> screenshotFrameSource.capture();
            case OBS -> obsFrameSource.capture();
            case NONE -> Optional.empty();
        };
    }
}
