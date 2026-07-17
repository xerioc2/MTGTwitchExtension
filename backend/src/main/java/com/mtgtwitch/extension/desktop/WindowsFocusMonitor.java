package com.mtgtwitch.extension.desktop;

import com.mtgtwitch.extension.gamestate.GameStateService;
import com.mtgtwitch.extension.log.MtgoLogWatcherService;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.platform.win32.WinNT.HANDLE;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.win32.StdCallLibrary;
import com.sun.jna.win32.W32APIOptions;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.awt.GraphicsEnvironment;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class WindowsFocusMonitor {

    private static final Logger log = LoggerFactory.getLogger(WindowsFocusMonitor.class);
    private static final Pattern GAME_TITLE_PATTERN = Pattern.compile(
            "\\bMatch\\s*#\\s*\\d+\\b.*?\\bGame\\s*#\\s*(\\d+)\\b",
            Pattern.CASE_INSENSITIVE
    );
    private static final int MAX_WINDOW_TITLE_LENGTH = 1024;
    private static final int PROCESS_QUERY_LIMITED_INFORMATION = 0x1000;

    private final boolean enabled;
    private final Duration pollInterval;
    private final GameStateService gameStateService;
    private final MtgoLogWatcherService mtgoLogWatcherService;
    private ScheduledExecutorService executorService;
    private ScheduledFuture<?> pollTask;
    private Long lastObservedGameId;

    public WindowsFocusMonitor(
            @Value("${mtgo.focus.enabled:true}") boolean enabled,
            @Value("${mtgo.focus.poll-interval:PT0.75S}") Duration pollInterval,
            GameStateService gameStateService,
            MtgoLogWatcherService mtgoLogWatcherService
    ) {
        this.enabled = enabled;
        this.pollInterval = pollInterval.isNegative() || pollInterval.isZero()
                ? Duration.ofMillis(750)
                : pollInterval;
        this.gameStateService = gameStateService;
        this.mtgoLogWatcherService = mtgoLogWatcherService;
    }

    @PostConstruct
    public void start() {
        if (!enabled) {
            log.info("MTGO foreground focus monitor is disabled.");
            return;
        }

        if (!isWindows() || GraphicsEnvironment.isHeadless()) {
            log.info("MTGO foreground focus monitor is inert outside a Windows desktop session.");
            return;
        }

        executorService = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "mtgo-focus-monitor");
            thread.setDaemon(true);
            return thread;
        });
        long intervalMillis = Math.max(100L, pollInterval.toMillis());
        pollTask = executorService.scheduleAtFixedRate(this::pollSafely, 0L, intervalMillis, TimeUnit.MILLISECONDS);
        log.info("MTGO foreground focus monitor started with {}ms polling.", intervalMillis);
    }

    @PreDestroy
    public void stop() {
        ScheduledFuture<?> activePollTask = pollTask;
        if (activePollTask != null) {
            activePollTask.cancel(true);
        }

        ScheduledExecutorService activeExecutorService = executorService;
        if (activeExecutorService != null) {
            activeExecutorService.shutdownNow();
        }
    }

    private void pollSafely() {
        try {
            pollForegroundWindow();
        } catch (Throwable throwable) {
            log.debug("MTGO foreground focus poll failed.", throwable);
        }
    }

    private void pollForegroundWindow() {
        HWND hwnd = User32.INSTANCE.GetForegroundWindow();
        if (hwnd == null || Pointer.nativeValue(hwnd.getPointer()) == 0L) {
            return;
        }

        String windowTitle = readWindowTitle(hwnd);
        OptionalLong gameId = extractGameIdFromTitle(windowTitle);
        if (gameId.isEmpty()) {
            return;
        }

        long nextGameId = gameId.getAsLong();
        if (lastObservedGameId != null && lastObservedGameId == nextGameId) {
            return;
        }

        lastObservedGameId = nextGameId;
        log.debug("MTGO foreground game focus changed: gameId={}, title={}", nextGameId, windowTitle);
        gameStateService.focusGame(nextGameId);

        resolveProcessImagePath(hwnd)
                .flatMap(WindowsFocusMonitor::deriveLogPathFromExecutable)
                .ifPresent(mtgoLogWatcherService::switchToFocusedLog);
    }

    private String readWindowTitle(HWND hwnd) {
        char[] title = new char[MAX_WINDOW_TITLE_LENGTH];
        int length = User32.INSTANCE.GetWindowText(hwnd, title, title.length);
        return length <= 0 ? "" : Native.toString(title);
    }

    private Optional<Path> resolveProcessImagePath(HWND hwnd) {
        IntByReference processId = new IntByReference();
        User32.INSTANCE.GetWindowThreadProcessId(hwnd, processId);
        if (processId.getValue() <= 0) {
            return Optional.empty();
        }

        HANDLE process = Kernel32Extra.INSTANCE.OpenProcess(
                PROCESS_QUERY_LIMITED_INFORMATION,
                false,
                processId.getValue()
        );
        if (process == null || Pointer.nativeValue(process.getPointer()) == 0L) {
            return Optional.empty();
        }

        try {
            char[] imagePath = new char[4096];
            IntByReference size = new IntByReference(imagePath.length);
            if (!Kernel32Extra.INSTANCE.QueryFullProcessImageName(process, 0, imagePath, size)) {
                return Optional.empty();
            }

            return Optional.of(Path.of(Native.toString(imagePath)));
        } finally {
            Kernel32Extra.INSTANCE.CloseHandle(process);
        }
    }

    static OptionalLong extractGameIdFromTitle(String title) {
        if (title == null || title.isBlank()) {
            return OptionalLong.empty();
        }

        Matcher matcher = GAME_TITLE_PATTERN.matcher(title);
        if (!matcher.find()) {
            return OptionalLong.empty();
        }

        try {
            return OptionalLong.of(Long.parseLong(matcher.group(1)));
        } catch (NumberFormatException exception) {
            return OptionalLong.empty();
        }
    }

    static Optional<Path> deriveLogPathFromExecutable(Path executablePath) {
        if (executablePath == null || executablePath.getFileName() == null) {
            return Optional.empty();
        }

        String fileName = executablePath.getFileName().toString().toLowerCase(Locale.ROOT);
        if (!fileName.endsWith(".exe") || !fileName.contains("mtgo")) {
            return Optional.empty();
        }

        Path installDirectory = executablePath.toAbsolutePath().normalize().getParent();
        if (installDirectory == null) {
            return Optional.empty();
        }

        return Optional.of(installDirectory.resolve("Logs").resolve("mtgo.log").normalize());
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    private interface Kernel32Extra extends StdCallLibrary {
        Kernel32Extra INSTANCE = Native.load("kernel32", Kernel32Extra.class, W32APIOptions.DEFAULT_OPTIONS);

        HANDLE OpenProcess(int desiredAccess, boolean inheritHandle, int processId);

        boolean QueryFullProcessImageName(HANDLE process, int flags, char[] imageName, IntByReference size);

        boolean CloseHandle(HANDLE handle);
    }
}
