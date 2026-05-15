package com.mtgtwitch.extension.gamestate;

public record CardZoneEvent(
        String cardName,
        Zone sourceZone,
        Zone destinationZone,
        String rawLine
) {
}
