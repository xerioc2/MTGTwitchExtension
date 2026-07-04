package com.mtgtwitch.extension.log;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

@Service
public class MtgoLogDiscoveryService {

    private static final Logger log = LoggerFactory.getLogger(MtgoLogDiscoveryService.class);
    private static final int MAX_LOG_PARENT_SCAN_DEPTH = 4;

    private final String configuredLogPath;
    private final Path searchRoot;

    @Autowired
    public MtgoLogDiscoveryService(@Value("${mtgo.log.path:}") String configuredLogPath) {
        this(configuredLogPath, null);
    }

    MtgoLogDiscoveryService(String configuredLogPath, Path searchRoot) {
        this.configuredLogPath = configuredLogPath;
        this.searchRoot = searchRoot;
    }

    public Optional<Path> resolveLogPath() {
        if (StringUtils.hasText(configuredLogPath)) {
            Path configuredPath = Path.of(configuredLogPath).toAbsolutePath().normalize();
            log.info("Using MTGO_LOG_PATH from configuration: {}", configuredPath);
            return Optional.of(configuredPath);
        }

        Path resolvedSearchRoot = searchRoot != null ? searchRoot : localAppDataAppsRoot();
        if (!Files.isDirectory(resolvedSearchRoot)) {
            log.error("MTGO_LOG_PATH is not configured and MTGO Apps root does not exist: {}", resolvedSearchRoot);
            return Optional.empty();
        }

        try (Stream<Path> candidates = Files.find(resolvedSearchRoot, MAX_LOG_PARENT_SCAN_DEPTH + 1, this::isMtgoLogCandidate)) {
            List<LogCandidate> logCandidates = candidates
                    .map(path -> new LogCandidate(path.toAbsolutePath().normalize(), lastModifiedTimeSafe(path)))
                    .toList();

            logCandidates.forEach(candidate -> log.info(
                    "MTGO log candidate: path={}, lastModified={}",
                    candidate.path(),
                    candidate.lastModified()
            ));

            Optional<Path> resolvedPath = logCandidates.stream()
                    .max(Comparator.comparing(LogCandidate::lastModified))
                    .map(LogCandidate::path);

            if (resolvedPath.isPresent()) {
                log.info("Auto-discovered MTGO log file: {}", resolvedPath.get());
            } else {
                log.error("No MTGO log file found under {} within directory depth {}.", resolvedSearchRoot, MAX_LOG_PARENT_SCAN_DEPTH);
            }

            return resolvedPath;
        } catch (IOException exception) {
            log.error("Failed while scanning for MTGO log files under {}.", resolvedSearchRoot, exception);
            return Optional.empty();
        }
    }

    private Path localAppDataAppsRoot() {
        String localAppData = System.getenv("LOCALAPPDATA");
        if (StringUtils.hasText(localAppData)) {
            return Path.of(localAppData, "Apps", "2.0").toAbsolutePath().normalize();
        }

        return Path.of(System.getProperty("user.home"), "AppData", "Local", "Apps", "2.0")
                .toAbsolutePath()
                .normalize();
    }

    private boolean isMtgoLogCandidate(Path path, java.nio.file.attribute.BasicFileAttributes attributes) {
        if (!attributes.isRegularFile()) {
            return false;
        }

        Path fileName = path.getFileName();
        Path parentName = path.getParent() != null ? path.getParent().getFileName() : null;

        return fileName != null
                && parentName != null
                && "mtgo.log".equalsIgnoreCase(fileName.toString())
                && "Logs".equalsIgnoreCase(parentName.toString());
    }

    private FileTime lastModifiedTimeSafe(Path path) {
        try {
            return Files.getLastModifiedTime(path);
        } catch (IOException exception) {
            log.debug("Could not read last modified time for MTGO log candidate {}.", path, exception);
            return FileTime.fromMillis(0);
        }
    }

    private record LogCandidate(Path path, FileTime lastModified) {
    }
}
