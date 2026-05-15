package com.mtgtwitch.extension.log;

import com.mtgtwitch.extension.gamestate.MtgoLogParserService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Service
public class MtgoLogWatcherService {

    private static final Logger log = LoggerFactory.getLogger(MtgoLogWatcherService.class);

    private final String configuredLogPath;
    private final MtgoLogParserService mtgoLogParserService;
    private final ExecutorService executorService;

    private volatile boolean running;
    private WatchService watchService;
    private Path logPath;
    private long lastKnownPosition;

    public MtgoLogWatcherService(
            @Value("${mtgo.log.path:}") String configuredLogPath,
            MtgoLogParserService mtgoLogParserService
    ) {
        this.configuredLogPath = configuredLogPath;
        this.mtgoLogParserService = mtgoLogParserService;
        this.executorService = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "mtgo-log-watcher");
            thread.setDaemon(true);
            return thread;
        });
    }

    @PostConstruct
    public void start() {
        if (!StringUtils.hasText(configuredLogPath)) {
            log.info("MTGO_LOG_PATH is not configured; MTGO log watcher is disabled.");
            return;
        }

        logPath = Path.of(configuredLogPath).toAbsolutePath().normalize();
        Path parentDirectory = logPath.getParent();

        if (parentDirectory == null) {
            log.warn("MTGO log watcher could not resolve a parent directory for '{}'.", logPath);
            return;
        }

        if (!Files.isDirectory(parentDirectory)) {
            log.warn("MTGO log watcher parent directory does not exist: {}", parentDirectory);
            return;
        }

        try {
            if (Files.exists(logPath)) {
                lastKnownPosition = Files.size(logPath);
            }

            watchService = logPath.getFileSystem().newWatchService();
            parentDirectory.register(
                    watchService,
                    StandardWatchEventKinds.ENTRY_CREATE,
                    StandardWatchEventKinds.ENTRY_MODIFY
            );
            running = true;
            executorService.submit(this::watchForChanges);
            log.info("MTGO log watcher started for {}", logPath);
        } catch (IOException exception) {
            log.error("Failed to start MTGO log watcher for {}", logPath, exception);
        }
    }

    @PreDestroy
    public void stop() {
        running = false;

        if (watchService != null) {
            try {
                watchService.close();
            } catch (IOException exception) {
                log.warn("Failed to close MTGO log watcher cleanly.", exception);
            }
        }

        executorService.shutdownNow();

        try {
            if (!executorService.awaitTermination(2, TimeUnit.SECONDS)) {
                log.warn("MTGO log watcher did not stop within the shutdown timeout.");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private void watchForChanges() {
        while (running) {
            WatchKey key;

            try {
                key = watchService.take();
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
                if (changedFile != null && changedFile.equals(logPath.getFileName())) {
                    readNewLines();
                }
            }

            if (!key.reset()) {
                log.warn("MTGO log watcher key is no longer valid for {}", logPath);
                return;
            }
        }
    }

    private void readNewLines() {
        if (!Files.exists(logPath)) {
            lastKnownPosition = 0;
            return;
        }

        try {
            long currentSize = Files.size(logPath);
            if (currentSize < lastKnownPosition) {
                log.info("MTGO log file appears to have been truncated; restarting from the beginning.");
                lastKnownPosition = 0;
            }

            try (RandomAccessFile file = new RandomAccessFile(logPath.toFile(), "r")) {
                file.seek(lastKnownPosition);

                String line;
                while ((line = file.readLine()) != null) {
                    String decodedLine = new String(line.getBytes(StandardCharsets.ISO_8859_1), StandardCharsets.UTF_8);
                    log.info("MTGO log line detected: {}", decodedLine);
                    mtgoLogParserService.parseAndApply(decodedLine);
                }

                lastKnownPosition = file.getFilePointer();
            }
        } catch (IOException exception) {
            log.warn("Failed to read new MTGO log lines from {}", logPath, exception);
        }
    }
}
