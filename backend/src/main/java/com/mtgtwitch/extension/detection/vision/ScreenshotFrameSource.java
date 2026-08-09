package com.mtgtwitch.extension.detection.vision;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.awt.AWTException;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.image.BufferedImage;
import java.time.Instant;
import java.util.Optional;

@Component
public class ScreenshotFrameSource implements FrameSource {

    private static final Logger log = LoggerFactory.getLogger(ScreenshotFrameSource.class);
    private final LocalVisionDetectorProperties properties;
    private final DesktopCapture desktopCapture;

    @Autowired
    public ScreenshotFrameSource(LocalVisionDetectorProperties properties) {
        this(properties, ScreenshotFrameSource::captureDesktop);
    }

    ScreenshotFrameSource(LocalVisionDetectorProperties properties, DesktopCapture desktopCapture) {
        this.properties = properties;
        this.desktopCapture = desktopCapture;
    }

    @Override
    public Optional<CapturedFrame> capture() {
        if (GraphicsEnvironment.isHeadless()) {
            return Optional.empty();
        }

        try {
            Rectangle captureBounds = properties.calibration().toPixelBounds(virtualDesktopBounds());
            BufferedImage image = desktopCapture.capture(captureBounds);
            return Optional.of(new CapturedFrame(
                    FrameImages.scaleToMaxWidth(image, properties.maxWidth()),
                    "SCREENSHOT",
                    Instant.now()
            ));
        } catch (RuntimeException | AWTException exception) {
            log.debug("Screen capture skipped: {}", exception.getMessage());
            return Optional.empty();
        }
    }

    public static Rectangle virtualDesktopBounds() {
        Rectangle bounds = null;
        for (GraphicsDevice device : GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices()) {
            Rectangle deviceBounds = device.getDefaultConfiguration().getBounds();
            bounds = bounds == null ? new Rectangle(deviceBounds) : bounds.union(deviceBounds);
        }
        return bounds == null ? new Rectangle(0, 0, 1, 1) : bounds;
    }

    private static BufferedImage captureDesktop(Rectangle bounds) throws AWTException {
        return new Robot().createScreenCapture(bounds);
    }

    @FunctionalInterface
    interface DesktopCapture {
        BufferedImage capture(Rectangle bounds) throws AWTException;
    }
}
