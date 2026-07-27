package com.mtgtwitch.extension.log;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class LogDiscoveryProgressTests {

    @AfterEach
    void resetProgress() {
        LogDiscoveryProgress.resetForTests();
    }

    @Test
    void progressMessageUpdatesAsCandidatesAreFound() {
        LogDiscoveryProgress.beginScan();
        assertThat(LogDiscoveryProgress.snapshot().message()).isEqualTo("Scanning 0 candidate logs...");

        LogDiscoveryProgress.candidateFound();
        assertThat(LogDiscoveryProgress.snapshot().message()).isEqualTo("Scanning 1 candidate log...");

        LogDiscoveryProgress.candidateFound();
        assertThat(LogDiscoveryProgress.snapshot().message()).isEqualTo("Scanning 2 candidate logs...");
    }

    @Test
    void completionTracksResolvedPath() {
        Path path = Path.of("C:\\MTGO\\Logs\\mtgo.log");

        LogDiscoveryProgress.beginScan();
        LogDiscoveryProgress.candidateFound();
        LogDiscoveryProgress.complete(path);

        LogDiscoveryProgress.Status status = LogDiscoveryProgress.snapshot();
        assertThat(status.scanning()).isFalse();
        assertThat(status.candidateCount()).isEqualTo(1);
        assertThat(status.path()).isEqualTo(path.toString());
        assertThat(status.message()).isEqualTo(path.toString());
        assertThat(status.watching()).isTrue();
    }
}
