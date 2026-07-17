package com.mtgtwitch.extension.desktop;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class WindowsFocusMonitorTests {

    @Test
    void extractsGameIdFromRealisticMtgoLeagueTitle() {
        assertThat(WindowsFocusMonitor.extractGameIdFromTitle(
                "Modern League: Stage 1 - Match 3 vs. Hypsh   League #10847 - Match #288295040 - Game #955650162"
        )).hasValue(955650162L);
    }

    @Test
    void extractsGameIdWithEmDashAndExtraWhitespace() {
        assertThat(WindowsFocusMonitor.extractGameIdFromTitle(
                "Legacy Challenge \u2014 Round 4 \u2014 Match # 288295041 \u2014 Game # 955650163"
        )).hasValue(955650163L);
    }

    @Test
    void ignoresNonGameWindowTitles() {
        assertThat(WindowsFocusMonitor.extractGameIdFromTitle("Magic Online")).isEmpty();
        assertThat(WindowsFocusMonitor.extractGameIdFromTitle("Collection")).isEmpty();
        assertThat(WindowsFocusMonitor.extractGameIdFromTitle("Notepad - Game #955650162")).isEmpty();
    }

    @Test
    void derivesMtgoLogPathFromProcessExecutable() {
        Path executable = Path.of("C:\\Users\\xerio\\AppData\\Local\\Apps\\2.0\\abc\\def\\MTGO.exe");

        assertThat(WindowsFocusMonitor.deriveLogPathFromExecutable(executable))
                .contains(Path.of("C:\\Users\\xerio\\AppData\\Local\\Apps\\2.0\\abc\\def\\Logs\\mtgo.log"));
    }

    @Test
    void ignoresNonMtgoExecutables() {
        assertThat(WindowsFocusMonitor.deriveLogPathFromExecutable(
                Path.of("C:\\Windows\\System32\\notepad.exe")
        )).isEmpty();
    }
}
