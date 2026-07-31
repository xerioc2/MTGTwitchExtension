package com.mtgtwitch.extension.gamestate;

import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Comparable game-state content used to suppress timestamp-only broadcasts.
 */
public record GameStateFingerprint(List<Object> values) {
    public static GameStateFingerprint from(GameState state) {
        List<Object> values = new ArrayList<>();
        for (RecordComponent component : GameState.class.getRecordComponents()) {
            if (component.getName().equals("updatedAt")) {
                continue;
            }

            try {
                values.add(component.getAccessor().invoke(state));
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("Could not fingerprint GameState.", exception);
            }
        }

        return new GameStateFingerprint(Collections.unmodifiableList(values));
    }
}
