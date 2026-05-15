package com.mtgtwitch.extension.log;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Optional;
import java.util.stream.Stream;

@Service
public class MtgoLogDiscoveryService {

    private static final Logger log = LoggerFactory.getLogger(MtgoLogDiscoveryService.class);
    private static final int MAX_LOG_PARENT_SCAN_DEPTH = 4;

    private final String configuredLogPath;

    public MtgoLogDiscoveryService(@Value("${mtgo.log.path:}") String configuredLogPath) {
        this.configuredLogPath = configuredLogPath;
    }

    public Optional<Path> resolveLogPath() {
        if (StringUtils.hasText(configuredLogPath)) {
            Path configuredPath = Path.of(configuredLogPath).toAbsolutePath().normalize();
            log.info("Using MTGO_LOG_PATH from configuration: {}", configuredPath);
            return Optional.of(configuredPath);
        }

        Path searchRoot = localAppDataAppsRoot();
        if (!Files.isDirectory(searchRoot)) {
            log.error("MTGO_LOG_PATH is not configured and MTGO Apps root does not exist: {}", searchRoot);
            return Optional.empty();
        }

        try (Stream<Path> candidates = Files.find(searchRoot, MAX_LOG_PARENT_SCAN_DEPTH + 1, this::isMtgoLogCandidate)) {
            Optional<Path> resolvedPath = candidates
                    .max(Comparator.comparing(this::lastModifiedTimeSafe))
                    .map(Path::toAbsolutePath)
                    .map(Path::normalize);

            if (resolvedPath.isPresent()) {
                log.info("Auto-discovered MTGO log file: {}", resolvedPath.get());
            } else {
                log.error("No MTGO log file found under {} within directory depth {}.", searchRoot, MAX_LOG_PARENT_SCAN_DEPTH);
            }

            return resolvedPath;
        } catch (IOException exception) {
            log.error("Failed while scanning for MTGO log files under {}.", searchRoot, exception);
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

    private java.nio.file.attribute.FileTime lastModifiedTimeSafe(Path path) {
        try {
            return Files.getLastModifiedTime(path);
        } catch (IOException exception) {
            log.debug("Could not read last modified time for MTGO log candidate {}.", path, exception);
            return java.nio.file.attribute.FileTime.fromMillis(0);
        }
    }
}
