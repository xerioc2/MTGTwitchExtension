package com.mtgtwitch.extension.detection.detector;

import com.mtgtwitch.extension.gamestate.GameState;

public record DetectorPublishResult(
        boolean published,
        String status,
        int regionCount,
        GameState gameState
) {
    public static DetectorPublishResult skipped(String status, GameState gameState) {
        return new DetectorPublishResult(false, status, 0, gameState);
    }

    public static DetectorPublishResult published(int regionCount, GameState gameState) {
        return new DetectorPublishResult(true, "published", regionCount, gameState);
    }
}
