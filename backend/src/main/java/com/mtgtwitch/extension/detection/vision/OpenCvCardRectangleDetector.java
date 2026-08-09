package com.mtgtwitch.extension.detection.vision;

import com.mtgtwitch.extension.detection.DetectionBbox;
import nu.pattern.OpenCV;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Rect;
import org.opencv.core.Size;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class OpenCvCardRectangleDetector {

    private static final Logger log = LoggerFactory.getLogger(OpenCvCardRectangleDetector.class);
    private static final double CARD_ASPECT_RATIO = 63.0 / 88.0;
    private static final AtomicBoolean LOAD_ATTEMPTED = new AtomicBoolean(false);
    private static volatile boolean available;

    private final LocalVisionDetectorProperties properties;

    public OpenCvCardRectangleDetector(LocalVisionDetectorProperties properties) {
        this.properties = properties;
    }

    public List<DetectedCardCandidate> detect(BufferedImage image) {
        if (image == null || !ensureLoaded()) {
            return List.of();
        }

        Mat color = new Mat();
        Mat gray = new Mat();
        Mat edges = new Mat();
        Mat hierarchy = new Mat();
        List<MatOfPoint> contours = new ArrayList<>();
        try {
            color = decode(image);
            if (color.empty()) {
                return List.of();
            }
            Imgproc.cvtColor(color, gray, Imgproc.COLOR_BGR2GRAY);
            Imgproc.GaussianBlur(gray, gray, new Size(5, 5), 0);
            Imgproc.Canny(gray, edges, 55, 150);
            Imgproc.morphologyEx(
                    edges,
                    edges,
                    Imgproc.MORPH_CLOSE,
                    Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(5, 5))
            );
            Imgproc.findContours(edges, contours, hierarchy, Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE);

            double frameArea = image.getWidth() * (double) image.getHeight();
            List<DetectedCardCandidate> candidates = new ArrayList<>();
            for (MatOfPoint contour : contours) {
                Rect rectangle = Imgproc.boundingRect(contour);
                double normalizedArea = rectangle.area() / frameArea;
                if (normalizedArea < properties.minArea() || normalizedArea > properties.maxArea()) {
                    continue;
                }
                int shortSide = Math.min(rectangle.width, rectangle.height);
                int longSide = Math.max(rectangle.width, rectangle.height);
                if (shortSide < 20 || longSide < 28) {
                    continue;
                }
                double aspectRatio = shortSide / (double) longSide;
                if (aspectRatio < 0.42 || aspectRatio > 0.92) {
                    continue;
                }

                double aspectConfidence = 1.0 - Math.min(1.0, Math.abs(aspectRatio - CARD_ASPECT_RATIO) / 0.35);
                DetectionBbox bbox = new DetectionBbox(
                        rectangle.x / (double) image.getWidth(),
                        rectangle.y / (double) image.getHeight(),
                        rectangle.width / (double) image.getWidth(),
                        rectangle.height / (double) image.getHeight()
                ).clamped();
                candidates.add(new DetectedCardCandidate(bbox, 0.45 + (0.55 * aspectConfidence)));
            }
            return suppressOverlaps(candidates, properties.maxRegions(), 0.55);
        } catch (Exception | LinkageError exception) {
            log.debug("OpenCV card rectangle detection skipped: {}", exception.getMessage());
            return List.of();
        } finally {
            color.release();
            gray.release();
            edges.release();
            hierarchy.release();
            contours.forEach(Mat::release);
        }
    }

    static List<DetectedCardCandidate> suppressOverlaps(
            List<DetectedCardCandidate> candidates,
            int maximum,
            double overlapThreshold
    ) {
        List<DetectedCardCandidate> sorted = candidates.stream()
                .sorted(Comparator.comparingDouble(DetectedCardCandidate::shapeConfidence).reversed())
                .toList();
        List<DetectedCardCandidate> kept = new ArrayList<>();
        for (DetectedCardCandidate candidate : sorted) {
            if (kept.stream().anyMatch(existing -> intersectionOverUnion(existing.bbox(), candidate.bbox()) >= overlapThreshold)) {
                continue;
            }
            kept.add(candidate);
            if (kept.size() >= maximum) {
                break;
            }
        }
        return List.copyOf(kept);
    }

    static double intersectionOverUnion(DetectionBbox left, DetectionBbox right) {
        double intersectionX = Math.max(left.x(), right.x());
        double intersectionY = Math.max(left.y(), right.y());
        double intersectionRight = Math.min(left.x() + left.w(), right.x() + right.w());
        double intersectionBottom = Math.min(left.y() + left.h(), right.y() + right.h());
        double intersectionArea = Math.max(0.0, intersectionRight - intersectionX)
                * Math.max(0.0, intersectionBottom - intersectionY);
        double unionArea = (left.w() * left.h()) + (right.w() * right.h()) - intersectionArea;
        return unionArea <= 0.0 ? 0.0 : intersectionArea / unionArea;
    }

    private static synchronized boolean ensureLoaded() {
        if (!LOAD_ATTEMPTED.compareAndSet(false, true)) {
            return available;
        }
        try {
            OpenCV.loadLocally();
            available = Core.VERSION != null;
        } catch (Throwable exception) {
            log.warn("OpenCV could not be loaded; local vision detection will remain inactive: {}", exception.getMessage());
            available = false;
        }
        return available;
    }

    private static Mat decode(BufferedImage image) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return Imgcodecs.imdecode(new MatOfByte(output.toByteArray()), Imgcodecs.IMREAD_COLOR);
    }
}
