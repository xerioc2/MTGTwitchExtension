package com.mtgtwitch.extension.desktop;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class WindowsStartupRegistryTests {

    @Test
    void regAddCommandQuotesExecutablePathWithSpaces() {
        String executablePath = "C:\\Users\\xerio\\MTGO Twitch Bridge\\MTGO Twitch Bridge.exe";

        List<String> command = WindowsStartupRegistry.regAddCommand(executablePath);

        assertThat(command).containsExactly(
                "reg.exe",
                "add",
                WindowsStartupRegistry.RUN_KEY,
                "/v",
                WindowsStartupRegistry.RUN_VALUE_NAME,
                "/t",
                "REG_SZ",
                "/d",
                "\"C:\\Users\\xerio\\MTGO Twitch Bridge\\MTGO Twitch Bridge.exe\"",
                "/f"
        );
    }

    @Test
    void selfHealDecisionWritesMissingOrStaleRunValueWhenPreferenceIsEnabled() {
        String executablePath = "C:\\Current\\MTGO Twitch Bridge.exe";

        assertThat(WindowsStartupRegistry.decideRegistryAction(true, null, executablePath))
                .isEqualTo(WindowsStartupRegistry.RegistryAction.WRITE);
        assertThat(WindowsStartupRegistry.decideRegistryAction(true, "\"C:\\Old\\MTGO Twitch Bridge.exe\"", executablePath))
                .isEqualTo(WindowsStartupRegistry.RegistryAction.WRITE);
    }

    @Test
    void selfHealDecisionLeavesMatchingRunValueWhenPreferenceIsEnabled() {
        String executablePath = "C:\\Current\\MTGO Twitch Bridge.exe";

        assertThat(WindowsStartupRegistry.decideRegistryAction(true, "\"C:\\Current\\MTGO Twitch Bridge.exe\"", executablePath))
                .isEqualTo(WindowsStartupRegistry.RegistryAction.LEAVE);
    }

    @Test
    void selfHealDecisionDeletesExistingRunValueWhenPreferenceIsDisabled() {
        assertThat(WindowsStartupRegistry.decideRegistryAction(false, "\"C:\\Current\\MTGO Twitch Bridge.exe\"", "C:\\Current\\MTGO Twitch Bridge.exe"))
                .isEqualTo(WindowsStartupRegistry.RegistryAction.DELETE);
        assertThat(WindowsStartupRegistry.decideRegistryAction(false, null, "C:\\Current\\MTGO Twitch Bridge.exe"))
                .isEqualTo(WindowsStartupRegistry.RegistryAction.LEAVE);
    }

    @Test
    void parsesRegQueryOutputValue() {
        String output = """
                HKEY_CURRENT_USER\\Software\\Microsoft\\Windows\\CurrentVersion\\Run
                    MTGOTwitchBridge    REG_SZ    "C:\\Users\\xerio\\MTGO Twitch Bridge\\MTGO Twitch Bridge.exe"
                """;

        assertThat(WindowsStartupRegistry.parseQueryValue(output))
                .contains("\"C:\\Users\\xerio\\MTGO Twitch Bridge\\MTGO Twitch Bridge.exe\"");
    }
}
