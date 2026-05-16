package com.mtgtwitch.extension.gamestate;

public record PlayerState(
        int id,
        String name,
        int libraryCount,
        int handCount,
        int life
) {
}
