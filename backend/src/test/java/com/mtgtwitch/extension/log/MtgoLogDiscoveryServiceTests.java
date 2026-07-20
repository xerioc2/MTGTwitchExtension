package com.mtgtwitch.extension.log;

import com.mtgtwitch.extension.desktop.MtgoAccountPreferences;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class MtgoLogDiscoveryServiceTests {

    @TempDir
    private Path tempDir;

    @Test
    void resolvesNewestMtgoLogCandidate() throws Exception {
        Path oldLog = tempDir.resolve("old-version").resolve("Logs").resolve("mtgo.log");
        Path newLog = tempDir.resolve("new-version").resolve("Logs").resolve("mtgo.log");
        Files.createDirectories(oldLog.getParent());
        Files.createDirectories(newLog.getParent());
        Files.writeString(oldLog, "old");
        Files.writeString(newLog, "new");
        Files.setLastModifiedTime(oldLog, FileTime.from(Instant.parse("2026-06-28T12:00:00Z")));
        Files.setLastModifiedTime(newLog, FileTime.from(Instant.parse("2026-07-04T12:00:00Z")));

        MtgoLogDiscoveryService service = new MtgoLogDiscoveryService("", tempDir);

        assertThat(service.resolveLogPath())
                .contains(newLog.toAbsolutePath().normalize());
    }

    @Test
    void resolvesNewestConfiguredAccountLogCandidate() throws Exception {
        Path xeriocLog = tempDir.resolve("xerioc").resolve("Logs").resolve("mtgo.log");
        Path dbLog = tempDir.resolve("db").resolve("Logs").resolve("mtgo.log");
        Path otherLog = tempDir.resolve("other").resolve("Logs").resolve("mtgo.log");
        Files.createDirectories(xeriocLog.getParent());
        Files.createDirectories(dbLog.getParent());
        Files.createDirectories(otherLog.getParent());
        Files.writeString(xeriocLog, "10:24:57 [INF] (Login|MtGO Login Success) Username: xerioc (1660533)\n");
        Files.writeString(dbLog, "10:25:14 [INF] (Login|MtGO Login Success) Username: DB_xerioc (3308837)\n");
        Files.writeString(otherLog, "10:25:20 [INF] (Login|MtGO Login Success) Username: someone_else (123)\n");
        Files.setLastModifiedTime(xeriocLog, FileTime.from(Instant.parse("2026-07-04T12:00:00Z")));
        Files.setLastModifiedTime(dbLog, FileTime.from(Instant.parse("2026-07-04T12:05:00Z")));
        Files.setLastModifiedTime(otherLog, FileTime.from(Instant.parse("2026-07-04T12:10:00Z")));

        MtgoLogDiscoveryService service = new MtgoLogDiscoveryService(
                "",
                tempDir,
                MtgoAccountPreferences.fixedForTests(java.util.List.of("xerioc", "DB_xerioc"))
        );

        assertThat(service.resolveLogPath())
                .contains(dbLog.toAbsolutePath().normalize());
    }

    @Test
    void extractsUsernameFromLoginAndDeckLines() {
        assertThat(MtgoLogDiscoveryService.extractUsername(
                "10:25:14 [INF] (Login|MtGO Login Success) Username: DB_xerioc (3308837)"
        )).contains("DB_xerioc");

        assertThat(MtgoLogDiscoveryService.extractUsername(
                "20:36:01 [INF] (Twitch Info|Username: DB_xerioc Deck Used in Game ID: 950571148) [NULL]"
        )).contains("DB_xerioc");
    }
}
