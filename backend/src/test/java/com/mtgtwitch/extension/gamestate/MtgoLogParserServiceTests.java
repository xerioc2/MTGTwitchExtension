package com.mtgtwitch.extension.gamestate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mtgtwitch.extension.websocket.GameStateBroadcaster;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class MtgoLogParserServiceTests {

    private final MtgoLogParserService parser = new MtgoLogParserService(null, new ObjectMapper());

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

    @Test
    void parsesTwitchDeckCatalogIds() {
        Optional<DeckCatalogEvent> event = parser.parseDeckCatalogEvent("""
                20:36:01 [INF] (Twitch Info|Username: DB_xerioc Deck Used in Game ID: 950571148) [{"CatalogId":126449,"Quantity":3,"Annotation":"NotSet","InSideboard":false},{"CatalogId":106607,"Quantity":4,"Annotation":"NotSet","InSideboard":false},{"CatalogId":126443,"Quantity":2,"Annotation":"NotSet","InSideboard":true}]
                """.trim());

        assertThat(event).hasValueSatisfying(deckEvent -> {
            assertThat(deckEvent.gameId()).isEqualTo(950571148);
            assertThat(deckEvent.deckCatalogIds()).containsExactly(126449, 106607, 126443);
        });
    }

    @Test
    void parsesTwitchGameStatusUpdate() {
        Optional<GameStatusEvent> event = parser.parseGameStatusEvent("""
                17:23:30 [INF] (Twitch Info|Game Play Status Update for Game ID: 950656092, Match ID: 287056035, Event ID: 287056035) {"Players":[{"Id":1,"Name":"DB_xerioc","LibraryCount":51,"HandCount":6,"Life":19},{"Id":0,"Name":"SaltySushi6","LibraryCount":50,"HandCount":5,"Life":16}],"Cards":[{"Id":423,"CatalogID":47471,"Zone":"Battlefield","ActualZone":"Battlefield","Owner":0,"Controller":0},{"Id":428,"CatalogID":122024,"Zone":"Battlefield","ActualZone":"Battlefield","Owner":1,"Controller":1},{"Id":425,"CatalogID":90565,"Zone":"Graveyard","ActualZone":"Graveyard","Owner":1,"Controller":1},{"Id":421,"CatalogID":78632,"Zone":"Hand","ActualZone":"Hand","Owner":1,"Controller":1},{"Id":441,"CatalogID":50256,"Zone":"Stack","ActualZone":"Stack","Owner":0,"Controller":0}]}
                """.trim());

        assertThat(event).hasValueSatisfying(statusEvent -> {
            assertThat(statusEvent.gameId()).isEqualTo(950656092);
            assertThat(statusEvent.matchId()).isEqualTo(287056035);
            assertThat(statusEvent.players()).extracting(PlayerState::name)
                    .containsExactly("DB_xerioc", "SaltySushi6");
            assertThat(statusEvent.cards()).extracting(GameCard::catalogId)
                    .containsExactly(47471, 122024, 90565, 78632, 50256);
        });
    }

    @Test
    void appliesTwitchGameStatusUpdateAsCurrentZoneSnapshotForLocalPlayer() {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        GameStateService gameStateService = new GameStateService(new GameStateBroadcaster(objectMapper));
        MtgoLogParserService applyingParser = new MtgoLogParserService(gameStateService, objectMapper);

        Optional<CardZoneEvent> cardZoneEvent = applyingParser.parseAndApply("""
                17:23:30 [INF] (Twitch Info|Game Play Status Update for Game ID: 950656092, Match ID: 287056035, Event ID: 287056035) {"Players":[{"Id":1,"Name":"DB_xerioc","LibraryCount":51,"HandCount":6,"Life":19},{"Id":0,"Name":"SaltySushi6","LibraryCount":50,"HandCount":5,"Life":16}],"Cards":[{"Id":423,"CatalogID":47471,"Zone":"Battlefield","ActualZone":"Battlefield","Owner":0,"Controller":0},{"Id":428,"CatalogID":122024,"Zone":"Battlefield","ActualZone":"Battlefield","Owner":1,"Controller":1},{"Id":425,"CatalogID":90565,"Zone":"Graveyard","ActualZone":"Graveyard","Owner":1,"Controller":1},{"Id":421,"CatalogID":78632,"Zone":"Hand","ActualZone":"Hand","Owner":1,"Controller":1},{"Id":430,"CatalogID":62689,"Zone":"Exile","ActualZone":"Exile","Owner":1,"Controller":1},{"Id":441,"CatalogID":50256,"Zone":"Stack","ActualZone":"Stack","Owner":0,"Controller":0}]}
                """.trim());

        GameState gameState = gameStateService.snapshot();
        assertThat(cardZoneEvent).isEmpty();
        assertThat(gameState.gameId()).isEqualTo(950656092);
        assertThat(gameState.hand()).containsExactly("CatalogID 78632");
        assertThat(gameState.battlefield()).containsExactly("CatalogID 122024");
        assertThat(gameState.graveyard()).containsExactly("CatalogID 90565");
        assertThat(gameState.exile()).containsExactly("CatalogID 62689");
        assertThat(gameState.handCards()).extracting(GameCard::catalogId).containsExactly(78632);
        assertThat(gameState.players()).extracting(PlayerState::life).containsExactly(19, 16);
    }

    @Test
    void appliesTwitchGameStatusUpdateForFirstPlayerWhenLocalPlayerIdIsZero() {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        GameStateService gameStateService = new GameStateService(new GameStateBroadcaster(objectMapper));
        MtgoLogParserService applyingParser = new MtgoLogParserService(gameStateService, objectMapper);

        Optional<CardZoneEvent> cardZoneEvent = applyingParser.parseAndApply("""
                20:52:35 [INF] (Twitch Info|Game Play Status Update for Game ID: 950776188, Match ID: 287085497, Event ID: 287085497) {"Players":[{"Id":0,"Name":"DB_xerioc","LibraryCount":32,"HandCount":2,"Life":14},{"Id":1,"Name":"DB_BFG","LibraryCount":36,"HandCount":5,"Life":4}],"Cards":[{"Id":604,"CatalogID":82270,"Zone":"Battlefield","ActualZone":"Battlefield","Owner":0,"Controller":0},{"Id":530,"CatalogID":130651,"Zone":"Battlefield","ActualZone":"Battlefield","Owner":1,"Controller":1},{"Id":472,"CatalogID":123066,"Zone":"Hand","ActualZone":"Hand","Owner":0,"Controller":0},{"Id":562,"CatalogID":144288,"Zone":"Graveyard","ActualZone":"Graveyard","Owner":1,"Controller":1},{"Id":608,"CatalogID":90881,"Zone":"Graveyard","ActualZone":"Graveyard","Owner":0,"Controller":0}]}
                """.trim());

        GameState gameState = gameStateService.snapshot();
        assertThat(cardZoneEvent).isEmpty();
        assertThat(gameState.gameId()).isEqualTo(950776188);
        assertThat(gameState.battlefieldCards()).extracting(GameCard::catalogId).containsExactly(82270);
        assertThat(gameState.handCards()).extracting(GameCard::catalogId).containsExactly(123066);
        assertThat(gameState.graveyardCards()).extracting(GameCard::catalogId).containsExactly(90881);
        assertThat(gameState.battlefieldCards()).extracting(GameCard::catalogId).doesNotContain(130651);
        assertThat(gameState.graveyardCards()).extracting(GameCard::catalogId).doesNotContain(144288);
    }
}
