package com.mtgtwitch.extension.log;

import com.mtgtwitch.extension.gamestate.MtgoLogParserService;
import com.mtgtwitch.extension.gamestate.DeckCard;
import com.mtgtwitch.extension.gamestate.GameState;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Service
public class MtgoLogWatcherService {

    private static final Logger log = LoggerFactory.getLogger(MtgoLogWatcherService.class);

    private final boolean rawLogDebugEnabled;
    private final long backfillBytes;
    private final MtgoLogDiscoveryService mtgoLogDiscoveryService;
    private final MtgoLogParserService mtgoLogParserService;
    private final ExecutorService executorService;
    private final ScheduledExecutorService pollingExecutorService;

    private volatile boolean running;
    private WatchService watchService;
    private Future<?> watcherTask;
    private ScheduledFuture<?> pollingTask;
    private Path logPath;
    private long lastKnownPosition;

    public MtgoLogWatcherService(
            @Value("${mtgo.debug.raw-log:false}") boolean rawLogDebugEnabled,
            @Value("${mtgo.log.backfill-bytes:5242880}") long backfillBytes,
            MtgoLogDiscoveryService mtgoLogDiscoveryService,
            MtgoLogParserService mtgoLogParserService
    ) {
        this.rawLogDebugEnabled = rawLogDebugEnabled;
        this.backfillBytes = Math.max(0, backfillBytes);
        this.mtgoLogDiscoveryService = mtgoLogDiscoveryService;
        this.mtgoLogParserService = mtgoLogParserService;
        this.executorService = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "mtgo-log-watcher");
            thread.setDaemon(true);
            return thread;
        });
        this.pollingExecutorService = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "mtgo-log-poller");
            thread.setDaemon(true);
            return thread;
        });
    }

    @PostConstruct
    public void start() {
        rescan();
    }

    public synchronized LogWatchStatus rescan() {
        stopCurrentWatcher();

        return mtgoLogDiscoveryService.resolveLogPath()
                .map(this::startWatching)
                .orElseGet(() -> new LogWatchStatus(null, false, "No MTGO log file was found."));
    }

    @PreDestroy
    public synchronized void stop() {
        stopCurrentWatcher();
        executorService.shutdownNow();
        pollingExecutorService.shutdownNow();

        try {
            if (!executorService.awaitTermination(2, TimeUnit.SECONDS)
                    || !pollingExecutorService.awaitTermination(2, TimeUnit.SECONDS)) {
                log.warn("MTGO log watcher did not stop within the shutdown timeout.");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private LogWatchStatus startWatching(Path resolvedLogPath) {
        logPath = resolvedLogPath.toAbsolutePath().normalize();
        Path parentDirectory = logPath.getParent();

        if (parentDirectory == null) {
            String message = "MTGO log watcher could not resolve a parent directory for '%s'; watcher is disabled."
                    .formatted(logPath);
            log.error(message);
            return new LogWatchStatus(logPath.toString(), false, message);
        }

        if (!Files.isDirectory(parentDirectory)) {
            String message = "MTGO log watcher parent directory does not exist: %s; watcher is disabled."
                    .formatted(parentDirectory);
            log.error(message);
            return new LogWatchStatus(logPath.toString(), false, message);
        }

        try {
            lastKnownPosition = backfillLogTail(logPath);
            if (rawLogDebugEnabled) {
                log.info("MTGO raw log debug mode is enabled; every new raw mtgo.log line will be written to the console.");
            }

            WatchService activeWatchService = logPath.getFileSystem().newWatchService();
            Path activeLogPath = logPath;
            watchService = activeWatchService;
            parentDirectory.register(
                    activeWatchService,
                    StandardWatchEventKinds.ENTRY_CREATE,
                    StandardWatchEventKinds.ENTRY_MODIFY
            );
            running = true;
            watcherTask = executorService.submit(() -> watchForChanges(activeWatchService, activeLogPath));
            pollingTask = pollingExecutorService.scheduleAtFixedRate(
                    () -> readNewLines(activeLogPath),
                    2,
                    2,
                    TimeUnit.SECONDS
            );

            if (!Files.exists(logPath)) {
                log.error("MTGO log file does not exist yet: {}. Watching {} for the file to be created.", logPath, parentDirectory);
            }

            log.info("MTGO log watcher started for {} with 2-second polling fallback.", logPath);
            return new LogWatchStatus(logPath.toString(), true, "MTGO log watcher started.");
        } catch (IOException exception) {
            log.error("Failed to start MTGO log watcher for {}", logPath, exception);
            return new LogWatchStatus(logPath.toString(), false, "Failed to start MTGO log watcher: " + exception.getMessage());
        }
    }

    private long backfillLogTail(Path activeLogPath) throws IOException {
        if (!Files.exists(activeLogPath)) {
            return 0;
        }

        long fileSize = Files.size(activeLogPath);
        long windowSize = Math.min(fileSize, backfillBytes);
        long startPosition = Math.max(0, fileSize - windowSize);
        int linesParsed = 0;
        Set<Long> gameIdsSeen = new LinkedHashSet<>();
        GameState resultingState;

        mtgoLogParserService.beginReplay();
        try (RandomAccessFile file = new RandomAccessFile(activeLogPath.toFile(), "r")) {
            file.seek(startPosition);
            if (startPosition > 0) {
                file.readLine();
            }

            String line;
            while ((line = file.readLine()) != null) {
                String decodedLine = new String(line.getBytes(StandardCharsets.ISO_8859_1), StandardCharsets.UTF_8);
                if (rawLogDebugEnabled) {
                    log.info("MTGO raw log backfill line: {}", decodedLine);
                }

                mtgoLogParserService.parseDeckCatalogEvent(decodedLine)
                        .ifPresent(event -> gameIdsSeen.add(event.gameId()));
                mtgoLogParserService.parseGameStatusEvent(decodedLine)
                        .ifPresent(event -> gameIdsSeen.add(event.gameId()));
                mtgoLogParserService.parseAndApply(decodedLine);
                linesParsed++;
            }

            resultingState = mtgoLogParserService.endReplay();
            long consumedPosition = file.getFilePointer();
            logBackfillSummary(windowSize, linesParsed, gameIdsSeen, resultingState);
            return consumedPosition;
        } catch (RuntimeException | IOException exception) {
            mtgoLogParserService.endReplay();
            throw exception;
        }
    }

    private void logBackfillSummary(long windowSize, int linesParsed, Set<Long> gameIdsSeen, GameState gameState) {
        int mainDeckCount = gameState.deckCards().stream()
                .filter(card -> !card.inSideboard())
                .mapToInt(DeckCard::quantity)
                .sum();
        int sideboardCount = gameState.deckCards().stream()
                .filter(DeckCard::inSideboard)
                .mapToInt(DeckCard::quantity)
                .sum();

        log.info(
                "MTGO log backfill complete: windowBytes={}, linesParsed={}, gameIdsSeen={}, activeGameId={}, deckMain={}, deckSideboard={}",
                windowSize,
                linesParsed,
                gameIdsSeen,
                gameState.gameId(),
                mainDeckCount,
                sideboardCount
        );
    }

    private void stopCurrentWatcher() {
        running = false;

        WatchService activeWatchService = watchService;
        if (activeWatchService != null) {
            try {
                activeWatchService.close();
            } catch (IOException exception) {
                log.warn("Failed to close MTGO log watcher cleanly.", exception);
            }
        }

        Future<?> activeWatcherTask = watcherTask;
        if (activeWatcherTask != null) {
            try {
                activeWatcherTask.get(2, TimeUnit.SECONDS);
            } catch (Exception exception) {
                activeWatcherTask.cancel(true);
            }
        }

        ScheduledFuture<?> activePollingTask = pollingTask;
        if (activePollingTask != null) {
            activePollingTask.cancel(true);
        }

        watchService = null;
        watcherTask = null;
        pollingTask = null;
    }

    private void watchForChanges(WatchService activeWatchService, Path activeLogPath) {
        while (running) {
            WatchKey key;

            try {
                key = activeWatchService.take();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return;
            } catch (ClosedWatchServiceException exception) {
                if (running) {
                    log.warn("MTGO log watcher stopped after watch service error.", exception);
                }
                return;
            }

            for (WatchEvent<?> event : key.pollEvents()) {
                if (event.kind() == StandardWatchEventKinds.OVERFLOW) {
                    continue;
                }

                Path changedFile = (Path) event.context();
                if (changedFile != null && changedFile.equals(activeLogPath.getFileName())) {
                    readNewLines(activeLogPath);
                }
            }

            if (!key.reset()) {
                log.warn("MTGO log watcher key is no longer valid for {}", activeLogPath);
                return;
            }
        }
    }

    private synchronized void readNewLines(Path activeLogPath) {
        if (!Files.exists(activeLogPath)) {
            lastKnownPosition = 0;
            return;
        }

        try {
            long currentSize = Files.size(activeLogPath);
            if (currentSize < lastKnownPosition) {
                log.info("MTGO log file appears to have been truncated; restarting from the beginning.");
                lastKnownPosition = 0;
            }

            try (RandomAccessFile file = new RandomAccessFile(activeLogPath.toFile(), "r")) {
                file.seek(lastKnownPosition);

                String line;
                while ((line = file.readLine()) != null) {
                    String decodedLine = new String(line.getBytes(StandardCharsets.ISO_8859_1), StandardCharsets.UTF_8);
                    if (rawLogDebugEnabled) {
                        log.info("MTGO raw log line: {}", decodedLine);
                    }
                    mtgoLogParserService.parseAndApply(decodedLine);
                }

                lastKnownPosition = file.getFilePointer();
            }
        } catch (IOException exception) {
            log.warn("Failed to read new MTGO log lines from {}", activeLogPath, exception);
        }
    }
}
