package com.mtgtwitch.extension.detection;

public record DetectionBbox(
        double x,
        double y,
        double w,
        double h
) {
    public DetectionBbox clamped() {
        double nextX = clamp01(x);
        double nextY = clamp01(y);
        double nextW = clamp01(w);
        double nextH = clamp01(h);

        if (nextX + nextW > 1.0) {
            nextW = 1.0 - nextX;
        }
        if (nextY + nextH > 1.0) {
            nextH = 1.0 - nextY;
        }

        return new DetectionBbox(nextX, nextY, nextW, nextH);
    }

    public boolean hasArea() {
        return w > 0.0 && h > 0.0;
    }

    private static double clamp01(double value) {
        if (!Double.isFinite(value)) {
            return 0.0;
        }

        return Math.max(0.0, Math.min(1.0, value));
    }
}
