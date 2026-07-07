package com.mtgtwitch.extension.log;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mtgtwitch.extension.gamestate.GameCard;
import com.mtgtwitch.extension.gamestate.GameState;
import com.mtgtwitch.extension.gamestate.GameStateService;
import com.mtgtwitch.extension.gamestate.MtgoLogParserService;
import com.mtgtwitch.extension.websocket.GameStateBroadcaster;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import static org.assertj.core.api.Assertions.assertThat;

class MtgoLogWatcherServiceTests {

    @TempDir
    private Path tempDir;

    @Test
    void backfillOnStartPopulatesDeckAndCurrentZonesImmediately() throws Exception {
        Path logPath = writeLog(deckLine(950571148L), statusLine(950571148L, 287056035L, 78632));
        Harness harness = new Harness(logPath, 5 * 1024 * 1024);

        harness.watcher.rescan();

        GameState gameState = harness.gameStateService.snapshot();
        assertThat(gameState.gameId()).isEqualTo(950571148L);
        assertThat(gameState.deckCards()).hasSize(3);
        assertThat(gameState.handCards()).extracting(GameCard::catalogId).containsExactly(78632);
        assertThat(harness.broadcaster.broadcastCount()).isEqualTo(1);

        harness.watcher.stop();
    }

    @Test
    void backfillDiscardsPartialFirstLineWhenWindowStartsMidFile() throws Exception {
        String partial = "this partial line should not become a card movement";
        String fullTail = deckLine(950571149L) + "\n" + statusLine(950571149L, 287056036L, 90565) + "\n";
        Path logPath = tempDir.resolve("mtgo.log");
        Files.writeString(logPath, partial + "\n" + fullTail);
        Harness harness = new Harness(logPath, fullTail.length() + 5L);

        harness.watcher.rescan();

        GameState gameState = harness.gameStateService.snapshot();
        assertThat(gameState.gameId()).isEqualTo(950571149L);
        assertThat(gameState.deckCards()).hasSize(3);
        assertThat(gameState.hand()).containsExactly("CatalogID 90565");

        harness.watcher.stop();
    }

    @Test
    void backfillBroadcastsOnceAndLaterLiveLinesBroadcastNormally() throws Exception {
        Path logPath = writeLog(deckLine(950571148L), statusLine(950571148L, 287056035L, 78632));
        Harness harness = new Harness(logPath, 5 * 1024 * 1024);

        harness.watcher.rescan();
        assertThat(harness.broadcaster.broadcastCount()).isEqualTo(1);

        Files.writeString(logPath, "Lightning Bolt is moved from hand to graveyard.\n", StandardOpenOption.APPEND);
        invokeReadNewLines(harness.watcher, logPath);

        assertThat(harness.broadcaster.broadcastCount()).isEqualTo(2);
        assertThat(harness.gameStateService.snapshot().graveyard()).contains("Lightning Bolt");

        harness.watcher.stop();
    }

    private Path writeLog(String... lines) throws Exception {
        Path logPath = tempDir.resolve("mtgo.log");
        Files.writeString(logPath, String.join("\n", lines) + "\n");
        return logPath;
    }

    private void invokeReadNewLines(MtgoLogWatcherService watcher, Path logPath) throws Exception {
        Method method = MtgoLogWatcherService.class.getDeclaredMethod("readNewLines", Path.class);
        method.setAccessible(true);
        method.invoke(watcher, logPath);
    }

    private String deckLine(long gameId) {
        return "20:36:01 [INF] (Twitch Info|Username: DB_xerioc Deck Used in Game ID: %d) [{\"CatalogId\":126449,\"Quantity\":3,\"Annotation\":\"NotSet\",\"InSideboard\":false},{\"CatalogId\":106607,\"Quantity\":4,\"Annotation\":\"NotSet\",\"InSideboard\":false},{\"CatalogId\":126443,\"Quantity\":2,\"Annotation\":\"NotSet\",\"InSideboard\":true}]"
                .formatted(gameId);
    }

    private String statusLine(long gameId, long matchId, int handCatalogId) {
        return "17:23:30 [INF] (Twitch Info|Game Play Status Update for Game ID: %d, Match ID: %d, Event ID: %d) {\"Players\":[{\"Id\":1,\"Name\":\"DB_xerioc\",\"LibraryCount\":51,\"HandCount\":6,\"Life\":19},{\"Id\":0,\"Name\":\"SaltySushi6\",\"LibraryCount\":50,\"HandCount\":5,\"Life\":16}],\"Cards\":[{\"Id\":421,\"CatalogID\":%d,\"Zone\":\"Hand\",\"ActualZone\":\"Hand\",\"Owner\":1,\"Controller\":1}]}"
                .formatted(gameId, matchId, matchId, handCatalogId);
    }

    private static class Harness {
        private final CountingBroadcaster broadcaster;
        private final GameStateService gameStateService;
        private final MtgoLogWatcherService watcher;

        private Harness(Path logPath, long backfillBytes) {
            ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
            broadcaster = new CountingBroadcaster(objectMapper);
            gameStateService = new GameStateService(broadcaster);
            MtgoLogParserService parser = new MtgoLogParserService(gameStateService, objectMapper);
            watcher = new MtgoLogWatcherService(
                    false,
                    backfillBytes,
                    new MtgoLogDiscoveryService(logPath.toString(), null),
                    parser
            );
        }
    }

    private static class CountingBroadcaster extends GameStateBroadcaster {
        private int broadcastCount;

        private CountingBroadcaster(ObjectMapper objectMapper) {
            super(objectMapper);
        }

        @Override
        public void broadcast(GameState gameState) {
            broadcastCount++;
            super.broadcast(gameState);
        }

        private int broadcastCount() {
            return broadcastCount;
        }
    }
}
