package com.mtgtwitch.extension.detection.vision;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class OcrTitleMatcher {

    private static final Logger log = LoggerFactory.getLogger(OcrTitleMatcher.class);
    private static final Duration PROCESS_TIMEOUT = Duration.ofSeconds(3);

    private final LocalVisionDetectorProperties properties;
    private final AtomicBoolean unavailableLogged = new AtomicBoolean(false);

    public OcrTitleMatcher(LocalVisionDetectorProperties properties) {
        this.properties = properties;
    }

    public Optional<OcrMatch> bestMatch(BufferedImage cardImage, List<KnownGameCard> knownCards) {
        if (!properties.ocrEnabled() || cardImage == null || knownCards == null || knownCards.isEmpty()) {
            return Optional.empty();
        }
        Optional<String> detectedText = readTitle(cardImage);
        if (detectedText.isEmpty()) {
            return Optional.empty();
        }
        return bestNameMatch(detectedText.get(), knownCards);
    }

    Optional<String> readTitle(BufferedImage cardImage) {
        Path temporaryImage = null;
        try {
            BufferedImage titleImage = prepareTitleImage(cardImage);
            temporaryImage = Files.createTempFile("mtgo-card-title-", ".png");
            ImageIO.write(titleImage, "png", temporaryImage.toFile());
            Process process = new ProcessBuilder(
                    properties.ocrExecutable(),
                    temporaryImage.toString(),
                    "stdout",
                    "-l",
                    "eng",
                    "--psm",
                    "7"
            ).redirectErrorStream(true).start();
            if (!process.waitFor(PROCESS_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                return Optional.empty();
            }
            String output = new String(process.getInputStream().readAllBytes()).trim();
            return output.isBlank() ? Optional.empty() : Optional.of(output.lines().findFirst().orElse(output).trim());
        } catch (Exception exception) {
            if (unavailableLogged.compareAndSet(false, true)) {
                log.info("OCR is enabled but '{}' could not be used: {}", properties.ocrExecutable(), exception.getMessage());
            }
            return Optional.empty();
        } finally {
            if (temporaryImage != null) {
                try {
                    Files.deleteIfExists(temporaryImage);
                } catch (Exception ignored) {
                    // Temporary OCR files are best-effort cleanup.
                }
            }
        }
    }

    static Optional<OcrMatch> bestNameMatch(String detectedText, List<KnownGameCard> knownCards) {
        String normalizedDetected = normalizeName(detectedText);
        if (normalizedDetected.length() < 3) {
            return Optional.empty();
        }
        OcrMatch best = null;
        for (KnownGameCard knownCard : knownCards) {
            String normalizedName = normalizeName(knownCard.details().name());
            double score = normalizedSimilarity(normalizedDetected, normalizedName);
            if (best == null || score > best.score()) {
                best = new OcrMatch(knownCard, score, detectedText.trim());
            }
        }
        return best == null || best.score() < 0.62 ? Optional.empty() : Optional.of(best);
    }

    static String normalizeName(String value) {
        if (value == null) {
            return "";
        }
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", " ")
                .trim()
                .replaceAll("\\s+", " ");
    }

    static double normalizedSimilarity(String left, String right) {
        int maximumLength = Math.max(left.length(), right.length());
        if (maximumLength == 0) {
            return 1.0;
        }
        return 1.0 - (levenshtein(left, right) / (double) maximumLength);
    }

    private static int levenshtein(String left, String right) {
        int[] previous = new int[right.length() + 1];
        for (int column = 0; column <= right.length(); column++) {
            previous[column] = column;
        }
        for (int row = 1; row <= left.length(); row++) {
            int[] current = new int[right.length() + 1];
            current[0] = row;
            for (int column = 1; column <= right.length(); column++) {
                int substitution = previous[column - 1]
                        + (left.charAt(row - 1) == right.charAt(column - 1) ? 0 : 1);
                current[column] = Math.min(
                        Math.min(current[column - 1] + 1, previous[column] + 1),
                        substitution
                );
            }
            previous = current;
        }
        return previous[right.length()];
    }

    private static BufferedImage prepareTitleImage(BufferedImage cardImage) {
        int titleHeight = Math.max(1, (int) Math.round(cardImage.getHeight() * 0.20));
        BufferedImage title = cardImage.getSubimage(0, 0, cardImage.getWidth(), titleHeight);
        int width = Math.max(320, title.getWidth() * 4);
        int height = Math.max(48, title.getHeight() * 4);
        BufferedImage prepared = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D graphics = prepared.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        graphics.drawImage(title, 0, 0, width, height, null);
        graphics.dispose();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int gray = new Color(prepared.getRGB(x, y)).getRed();
                int value = gray < 150 ? 0 : 255;
                prepared.getRaster().setSample(x, y, 0, value);
            }
        }
        return prepared;
    }

    public record OcrMatch(KnownGameCard card, double score, String detectedText) {
    }
}
