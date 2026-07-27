package com.mtgtwitch.extension.log;

import com.mtgtwitch.extension.desktop.MtgoAccountPreferences;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@Service
public class MtgoLogDiscoveryService {

    private static final Logger log = LoggerFactory.getLogger(MtgoLogDiscoveryService.class);
    private static final int MAX_LOG_PARENT_SCAN_DEPTH = 4;
    private static final long USERNAME_HEAD_SCAN_BYTES = 1024 * 1024;
    private static final long USERNAME_TAIL_SCAN_BYTES = 16L * 1024 * 1024;
    private static final Pattern LOGIN_USERNAME_PATTERN = Pattern.compile(
            "\\(Login\\|MtGO Login (?:Last Success|Success)\\)\\s+Username:\\s+(.+?)(?:\\s+\\(\\d+\\))?$",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern TWITCH_INFO_USERNAME_PATTERN = Pattern.compile(
            "\\(Twitch Info\\|Username:\\s*(.+?)\\s+Deck Used",
            Pattern.CASE_INSENSITIVE
    );

    private final String configuredLogPath;
    private final Path searchRoot;
    private final MtgoAccountPreferences mtgoAccountPreferences;

    @Autowired
    public MtgoLogDiscoveryService(
            @Value("${mtgo.log.path:}") String configuredLogPath,
            MtgoAccountPreferences mtgoAccountPreferences
    ) {
        this(configuredLogPath, null, mtgoAccountPreferences);
    }

    MtgoLogDiscoveryService(String configuredLogPath, Path searchRoot) {
        this(configuredLogPath, searchRoot, MtgoAccountPreferences.fixedForTests(List.of()));
    }

    MtgoLogDiscoveryService(String configuredLogPath, Path searchRoot, MtgoAccountPreferences mtgoAccountPreferences) {
        this.configuredLogPath = configuredLogPath;
        this.searchRoot = searchRoot;
        this.mtgoAccountPreferences = mtgoAccountPreferences;
    }

    public Optional<Path> resolveLogPath() {
        return listCandidates().stream()
                .max(Comparator.comparing(LogCandidate::lastModified))
                .map(LogCandidate::path);
    }

    public List<LogCandidate> listCandidates() {
        LogDiscoveryProgress.beginScan();
        if (StringUtils.hasText(configuredLogPath)) {
            Path configuredPath = Path.of(configuredLogPath).toAbsolutePath().normalize();
            log.info("Using MTGO_LOG_PATH from configuration: {}", configuredPath);
            LogDiscoveryProgress.candidateFound();
            LogDiscoveryProgress.complete(configuredPath);
            return List.of(new LogCandidate(configuredPath, lastModifiedTimeSafe(configuredPath)));
        }

        Path resolvedSearchRoot = searchRoot != null ? searchRoot : localAppDataAppsRoot();
        if (!Files.isDirectory(resolvedSearchRoot)) {
            log.error("MTGO_LOG_PATH is not configured and MTGO Apps root does not exist: {}", resolvedSearchRoot);
            LogDiscoveryProgress.fail("MTGO Apps root does not exist: " + resolvedSearchRoot);
            return List.of();
        }

        try (Stream<Path> candidates = Files.find(resolvedSearchRoot, MAX_LOG_PARENT_SCAN_DEPTH + 1, this::isMtgoLogCandidate)) {
            List<LogCandidate> logCandidates = candidates
                    .map(path -> {
                        LogDiscoveryProgress.candidateFound();
                        return new LogCandidate(path.toAbsolutePath().normalize(), lastModifiedTimeSafe(path));
                    })
                    .toList();

            logCandidates.forEach(candidate -> log.info(
                    "MTGO log candidate: path={}, lastModified={}, username={}, allowed={}",
                    candidate.path(),
                    candidate.lastModified(),
                    resolveCandidateUsername(candidate.path()).orElse("unknown"),
                    isLogAllowed(candidate.path())
            ));

            logCandidates = filterConfiguredAccountLogs(logCandidates);

            Optional<Path> resolvedPath = logCandidates.stream()
                    .max(Comparator.comparing(LogCandidate::lastModified))
                    .map(LogCandidate::path);

            if (resolvedPath.isPresent()) {
                log.info("Auto-discovered MTGO log file: {}", resolvedPath.get());
                LogDiscoveryProgress.complete(resolvedPath.get());
            } else {
                log.error("No MTGO log file found under {} within directory depth {}.", resolvedSearchRoot, MAX_LOG_PARENT_SCAN_DEPTH);
                LogDiscoveryProgress.complete(null);
            }

            return logCandidates;
        } catch (IOException exception) {
            log.error("Failed while scanning for MTGO log files under {}.", resolvedSearchRoot, exception);
            LogDiscoveryProgress.fail("Failed while scanning for MTGO log files under " + resolvedSearchRoot + ".");
            return List.of();
        }
    }

    public boolean isLogAllowed(Path path) {
        if (!mtgoAccountPreferences.hasConfiguredUsernames()) {
            return true;
        }

        return resolveCandidateUsername(path)
                .filter(mtgoAccountPreferences::allowsUsername)
                .isPresent();
    }

    public Optional<String> resolveCandidateUsername(Path path) {
        if (path == null || !Files.exists(path)) {
            return Optional.empty();
        }

        try (RandomAccessFile file = new RandomAccessFile(path.toFile(), "r")) {
            long fileSize = file.length();
            String username = scanUsernameWindow(file, 0, Math.min(fileSize, USERNAME_HEAD_SCAN_BYTES), false);
            long tailStart = Math.max(0, fileSize - USERNAME_TAIL_SCAN_BYTES);
            if (tailStart > USERNAME_HEAD_SCAN_BYTES) {
                String tailUsername = scanUsernameWindow(file, tailStart, fileSize, true);
                if (tailUsername != null) {
                    username = tailUsername;
                }
            }

            return Optional.ofNullable(username);
        } catch (IOException exception) {
            log.debug("Could not inspect MTGO log username for {}.", path, exception);
            return Optional.empty();
        }
    }

    private String scanUsernameWindow(RandomAccessFile file, long startPosition, long endPosition, boolean discardPartialLine) throws IOException {
        file.seek(startPosition);
        if (discardPartialLine && startPosition > 0) {
            file.readLine();
        }

        String username = null;
        String line;
        while (file.getFilePointer() <= endPosition && (line = file.readLine()) != null) {
            String decodedLine = new String(line.getBytes(StandardCharsets.ISO_8859_1), StandardCharsets.UTF_8);
            Optional<String> usernameFromLine = extractUsername(decodedLine);
            if (usernameFromLine.isPresent()) {
                username = usernameFromLine.get();
            }
        }

        return username;
    }

    static Optional<String> extractUsername(String line) {
        if (line == null || line.isBlank()) {
            return Optional.empty();
        }

        Matcher loginMatcher = LOGIN_USERNAME_PATTERN.matcher(line.trim());
        if (loginMatcher.find()) {
            return Optional.of(loginMatcher.group(1).trim());
        }

        Matcher twitchInfoMatcher = TWITCH_INFO_USERNAME_PATTERN.matcher(line.trim());
        if (twitchInfoMatcher.find()) {
            return Optional.of(twitchInfoMatcher.group(1).trim());
        }

        return Optional.empty();
    }

    private List<LogCandidate> filterConfiguredAccountLogs(List<LogCandidate> logCandidates) {
        if (!mtgoAccountPreferences.hasConfiguredUsernames()) {
            return logCandidates;
        }

        List<LogCandidate> allowedCandidates = logCandidates.stream()
                .filter(candidate -> isLogAllowed(candidate.path()))
                .toList();
        if (allowedCandidates.isEmpty()) {
            log.warn(
                    "Configured MTGO accounts are {}, but no matching mtgo.log candidates were found.",
                    mtgoAccountPreferences.usernames()
            );
        }

        return allowedCandidates;
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

    public record LogCandidate(Path path, FileTime lastModified) {
    }
}
