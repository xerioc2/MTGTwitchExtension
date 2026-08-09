package com.mtgtwitch.extension.detection.vision;

import com.mtgtwitch.extension.detection.DetectionBbox;

import java.awt.Rectangle;

public record ScreenCalibration(double x, double y, double w, double h) {

    public ScreenCalibration {
        x = clamp(x);
        y = clamp(y);
        w = clamp(w);
        h = clamp(h);
        if (x + w > 1.0) {
            w = 1.0 - x;
        }
        if (y + h > 1.0) {
            h = 1.0 - y;
        }
    }

    public Rectangle toPixelBounds(Rectangle desktopBounds) {
        if (desktopBounds == null || desktopBounds.width <= 0 || desktopBounds.height <= 0 || w <= 0 || h <= 0) {
            throw new IllegalArgumentException("Calibration and desktop bounds must have positive area.");
        }

        int left = desktopBounds.x + (int) Math.round(x * desktopBounds.width);
        int top = desktopBounds.y + (int) Math.round(y * desktopBounds.height);
        int width = Math.max(1, (int) Math.round(w * desktopBounds.width));
        int height = Math.max(1, (int) Math.round(h * desktopBounds.height));
        width = Math.min(width, desktopBounds.x + desktopBounds.width - left);
        height = Math.min(height, desktopBounds.y + desktopBounds.height - top);
        return new Rectangle(left, top, width, height);
    }

    public DetectionBbox mapToParent(DetectionBbox localBbox) {
        if (localBbox == null) {
            throw new IllegalArgumentException("Local bounding box is required.");
        }
        return new DetectionBbox(
                x + (localBbox.x() * w),
                y + (localBbox.y() * h),
                localBbox.w() * w,
                localBbox.h() * h
        ).clamped();
    }

    private static double clamp(double value) {
        if (!Double.isFinite(value)) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(1.0, value));
    }
}
