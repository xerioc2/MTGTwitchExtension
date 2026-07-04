package com.mtgtwitch.extension.log;

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
}
