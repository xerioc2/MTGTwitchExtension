package com.mtgtwitch.extension.desktop;

import org.junit.jupiter.api.Test;

import java.util.OptionalInt;

import static org.assertj.core.api.Assertions.assertThat;

class MtgoBridgeLauncherTests {

    @Test
    void quietIfRunningOnlyAppliesWhenARealBridgeIsAlreadyRunning() {
        BridgeInstanceGuard.ScanResult bridgeRunning = BridgeInstanceGuard.ScanResult.bridgeRunning(8080);

        assertThat(MtgoBridgeLauncher.shouldExitQuietlyIfRunning(
                new String[]{"--quiet-if-running"},
                bridgeRunning
        )).isTrue();

        assertThat(MtgoBridgeLauncher.shouldExitQuietlyIfRunning(
                new String[]{"--server.port=8080"},
                bridgeRunning
        )).isFalse();

        assertThat(MtgoBridgeLauncher.shouldExitQuietlyIfRunning(
                new String[]{"--quiet-if-running"},
                BridgeInstanceGuard.ScanResult.available(8081)
        )).isFalse();

        assertThat(MtgoBridgeLauncher.shouldExitQuietlyIfRunning(
                new String[]{"--quiet-if-running"},
                new BridgeInstanceGuard.ScanResult(
                        BridgeInstanceGuard.ScanStatus.NONE_AVAILABLE,
                        OptionalInt.empty()
                )
        )).isFalse();
    }

    @Test
    void quietFlagIsCaseInsensitive() {
        assertThat(MtgoBridgeLauncher.hasQuietIfRunningFlag(new String[]{"--QUIET-IF-RUNNING"}))
                .isTrue();
        assertThat(MtgoBridgeLauncher.hasQuietIfRunningFlag(new String[]{"--server.port=8080"}))
                .isFalse();
        assertThat(MtgoBridgeLauncher.hasQuietIfRunningFlag(null))
                .isFalse();
    }
}
