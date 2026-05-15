package com.mtgtwitch.extension.gamestate;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class MtgoLogParserServiceTests {

    private final MtgoLogParserService parser = new MtgoLogParserService(null);

    @Test
    void parsesMoveBetweenTrackedZones() {
        Optional<CardZoneEvent> event = parser.parse("Lightning Bolt is moved from hand to graveyard.");

        assertThat(event).hasValueSatisfying(zoneEvent -> {
            assertThat(zoneEvent.cardName()).isEqualTo("Lightning Bolt");
            assertThat(zoneEvent.sourceZone()).isEqualTo(Zone.HAND);
            assertThat(zoneEvent.destinationZone()).isEqualTo(Zone.GRAVEYARD);
        });
    }

    @Test
    void parsesActorMoveIntoExile() {
        Optional<CardZoneEvent> event = parser.parse("Xerio puts Swords to Plowshares into exile.");

        assertThat(event).hasValueSatisfying(zoneEvent -> {
            assertThat(zoneEvent.cardName()).isEqualTo("Swords to Plowshares");
            assertThat(zoneEvent.sourceZone()).isNull();
            assertThat(zoneEvent.destinationZone()).isEqualTo(Zone.EXILE);
        });
    }

    @Test
    void parsesEntersBattlefield() {
        Optional<CardZoneEvent> event = parser.parse("Delver of Secrets enters the battlefield.");

        assertThat(event).hasValueSatisfying(zoneEvent -> {
            assertThat(zoneEvent.cardName()).isEqualTo("Delver of Secrets");
            assertThat(zoneEvent.sourceZone()).isNull();
            assertThat(zoneEvent.destinationZone()).isEqualTo(Zone.BATTLEFIELD);
        });
    }

    @Test
    void parsesPlayerDiscard() {
        Optional<CardZoneEvent> event = parser.parse("Opponent discards Thoughtseize.");

        assertThat(event).hasValueSatisfying(zoneEvent -> {
            assertThat(zoneEvent.cardName()).isEqualTo("Thoughtseize");
            assertThat(zoneEvent.sourceZone()).isEqualTo(Zone.HAND);
            assertThat(zoneEvent.destinationZone()).isEqualTo(Zone.GRAVEYARD);
        });
    }

    @Test
    void parsesPlayerExile() {
        Optional<CardZoneEvent> event = parser.parse("Xerio exiles Solitude.");

        assertThat(event).hasValueSatisfying(zoneEvent -> {
            assertThat(zoneEvent.cardName()).isEqualTo("Solitude");
            assertThat(zoneEvent.sourceZone()).isNull();
            assertThat(zoneEvent.destinationZone()).isEqualTo(Zone.EXILE);
        });
    }

    @Test
    void parsesPlayerDrawOfNamedCard() {
        Optional<CardZoneEvent> event = parser.parse("Xerio draws Brainstorm.");

        assertThat(event).hasValueSatisfying(zoneEvent -> {
            assertThat(zoneEvent.cardName()).isEqualTo("Brainstorm");
            assertThat(zoneEvent.sourceZone()).isNull();
            assertThat(zoneEvent.destinationZone()).isEqualTo(Zone.HAND);
        });
    }

    @Test
    void parsesPlayerCastAsBattlefieldEventForCurrentModel() {
        Optional<CardZoneEvent> event = parser.parse("Xerio casts Ragavan, Nimble Pilferer.");

        assertThat(event).hasValueSatisfying(zoneEvent -> {
            assertThat(zoneEvent.cardName()).isEqualTo("Ragavan, Nimble Pilferer");
            assertThat(zoneEvent.sourceZone()).isEqualTo(Zone.HAND);
            assertThat(zoneEvent.destinationZone()).isEqualTo(Zone.BATTLEFIELD);
        });
    }
}
