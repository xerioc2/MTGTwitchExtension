package com.mtgtwitch.extension.detection.vision;

import com.mtgtwitch.extension.detection.DetectionBbox;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OpenCvCardRectangleDetectorTests {

    @Test
    void detectsPortraitCardRectangleInSyntheticFrame() {
        BufferedImage image = new BufferedImage(800, 450, BufferedImage.TYPE_3BYTE_BGR);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(Color.DARK_GRAY);
        graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
        graphics.setColor(Color.WHITE);
        graphics.fillRect(200, 100, 126, 176);
        graphics.setColor(Color.BLACK);
        graphics.fillRect(205, 105, 116, 166);
        graphics.dispose();

        List<DetectedCardCandidate> candidates = new OpenCvCardRectangleDetector(properties()).detect(image);

        assertThat(candidates).anySatisfy(candidate -> {
            assertThat(candidate.bbox().x()).isBetween(0.24, 0.26);
            assertThat(candidate.bbox().y()).isBetween(0.21, 0.24);
            assertThat(candidate.bbox().w()).isBetween(0.14, 0.17);
            assertThat(candidate.bbox().h()).isBetween(0.37, 0.41);
        });
    }

    @Test
    void detectsTappedLandscapeCardRectangleInSyntheticFrame() {
        BufferedImage image = new BufferedImage(800, 450, BufferedImage.TYPE_3BYTE_BGR);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(Color.DARK_GRAY);
        graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
        graphics.setColor(Color.WHITE);
        graphics.fillRect(200, 100, 176, 126);
        graphics.setColor(Color.BLACK);
        graphics.fillRect(205, 105, 166, 116);
        graphics.dispose();

        List<DetectedCardCandidate> candidates = new OpenCvCardRectangleDetector(properties()).detect(image);

        assertThat(candidates).anySatisfy(candidate -> {
            assertThat(candidate.bbox().x()).isBetween(0.24, 0.26);
            assertThat(candidate.bbox().y()).isBetween(0.21, 0.24);
            assertThat(candidate.bbox().w()).isBetween(0.20, 0.24);
            assertThat(candidate.bbox().h()).isBetween(0.26, 0.30);
        });
    }

    @Test
    void overlapSuppressionKeepsBestCandidateAndDistinctCards() {
        DetectedCardCandidate strongest = candidate(0.10, 0.10, 0.10, 0.18, 0.95);
        DetectedCardCandidate duplicate = candidate(0.102, 0.102, 0.10, 0.18, 0.70);
        DetectedCardCandidate distinct = candidate(0.40, 0.10, 0.10, 0.18, 0.80);

        List<DetectedCardCandidate> result = OpenCvCardRectangleDetector.suppressOverlaps(
                List.of(duplicate, distinct, strongest),
                10,
                0.72
        );

        assertThat(result).containsExactly(strongest, distinct);
    }

    @Test
    void intersectionOverUnionIsZeroForSeparateBoxes() {
        double overlap = OpenCvCardRectangleDetector.intersectionOverUnion(
                new DetectionBbox(0.0, 0.0, 0.1, 0.1),
                new DetectionBbox(0.5, 0.5, 0.1, 0.1)
        );

        assertThat(overlap).isZero();
    }

    private static DetectedCardCandidate candidate(
            double x,
            double y,
            double width,
            double height,
            double confidence
    ) {
        return new DetectedCardCandidate(new DetectionBbox(x, y, width, height), confidence);
    }

    private static LocalVisionDetectorProperties properties() {
        return new LocalVisionDetectorProperties(
                "screenshot",
                1280,
                0.0,
                0.0,
                1.0,
                1.0,
                "ws://127.0.0.1:4455",
                "",
                "",
                0.0025,
                0.25,
                20,
                false,
                "tesseract",
                true,
                0.72
        );
    }
}
