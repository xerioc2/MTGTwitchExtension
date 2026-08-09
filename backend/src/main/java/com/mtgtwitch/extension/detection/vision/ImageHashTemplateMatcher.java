package com.mtgtwitch.extension.detection.vision;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ImageHashTemplateMatcher {

    private static final Logger log = LoggerFactory.getLogger(ImageHashTemplateMatcher.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(4);

    private final HttpClient httpClient;
    private final Map<Integer, Optional<Long>> templateHashes = new ConcurrentHashMap<>();

    @Autowired
    public ImageHashTemplateMatcher() {
        this(HttpClient.newBuilder().connectTimeout(TIMEOUT).build());
    }

    ImageHashTemplateMatcher(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    public Optional<TemplateMatch> bestMatch(BufferedImage candidate, List<KnownGameCard> knownCards) {
        if (candidate == null || knownCards == null || knownCards.isEmpty()) {
            return Optional.empty();
        }
        long candidateHash = differenceHash(candidate);
        TemplateMatch best = null;
        for (KnownGameCard knownCard : knownCards) {
            Optional<Long> templateHash = templateHashes.computeIfAbsent(
                    knownCard.gameCard().catalogId(),
                    ignored -> loadTemplateHash(knownCard)
            );
            if (templateHash.isEmpty()) {
                continue;
            }
            double score = hashSimilarity(candidateHash, templateHash.get());
            if (best == null || score > best.score()) {
                best = new TemplateMatch(knownCard, score);
            }
        }
        return Optional.ofNullable(best);
    }

    static long differenceHash(BufferedImage image) {
        BufferedImage scaled = new BufferedImage(9, 8, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = scaled.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        graphics.drawImage(image, 0, 0, 9, 8, null);
        graphics.dispose();

        long hash = 0L;
        int bit = 0;
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 8; x++) {
                int left = luminance(new Color(scaled.getRGB(x, y)));
                int right = luminance(new Color(scaled.getRGB(x + 1, y)));
                if (left > right) {
                    hash |= 1L << bit;
                }
                bit++;
            }
        }
        return hash;
    }

    static double hashSimilarity(long left, long right) {
        return 1.0 - (Long.bitCount(left ^ right) / 64.0);
    }

    private Optional<Long> loadTemplateHash(KnownGameCard knownCard) {
        String imageUrl = knownCard.details().imageUrl();
        if (imageUrl == null || imageUrl.isBlank()) {
            return Optional.empty();
        }
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(imageUrl))
                    .timeout(TIMEOUT)
                    .header("User-Agent", "MTGO-Twitch-Bridge/0.0.14")
                    .GET()
                    .build();
            HttpResponse<java.io.InputStream> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofInputStream()
            );
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return Optional.empty();
            }
            try (java.io.InputStream stream = response.body()) {
                BufferedImage image = ImageIO.read(stream);
                return image == null ? Optional.empty() : Optional.of(differenceHash(image));
            }
        } catch (Exception exception) {
            log.debug("Card template image could not be loaded: {}", exception.getMessage());
            return Optional.empty();
        }
    }

    private static int luminance(Color color) {
        return (int) Math.round((0.299 * color.getRed()) + (0.587 * color.getGreen()) + (0.114 * color.getBlue()));
    }

    public record TemplateMatch(KnownGameCard card, double score) {
    }
}
