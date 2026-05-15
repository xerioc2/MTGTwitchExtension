package com.mtgtwitch.extension.gamestate;

import java.util.Locale;
import java.util.Optional;

public enum Zone {
    HAND,
    BATTLEFIELD,
    GRAVEYARD,
    EXILE;

    public static Optional<Zone> fromLogText(String text) {
        String normalized = text.toLowerCase(Locale.ROOT).trim();

        if (normalized.contains("hand")) {
            return Optional.of(HAND);
        }
        if (normalized.contains("battlefield") || normalized.contains("play")) {
            return Optional.of(BATTLEFIELD);
        }
        if (normalized.contains("graveyard")) {
            return Optional.of(GRAVEYARD);
        }
        if (normalized.contains("exile") || normalized.contains("removed from the game")) {
            return Optional.of(EXILE);
        }

        return Optional.empty();
    }
}
