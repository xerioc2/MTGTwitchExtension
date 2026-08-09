package com.mtgtwitch.extension.detection.vision;

import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class DetectionConfidenceScorer {

    public Optional<ScoredCardMatch> resolve(
            double shapeConfidence,
            Optional<ImageHashTemplateMatcher.TemplateMatch> templateMatch,
            Optional<OcrTitleMatcher.OcrMatch> ocrMatch
    ) {
        double shape = clamp(shapeConfidence);
        if (templateMatch.isEmpty() && ocrMatch.isEmpty()) {
            return Optional.empty();
        }

        if (templateMatch.isPresent() && templateMatch.get().score() < 0.58) {
            templateMatch = Optional.empty();
        }
        if (ocrMatch.isPresent() && ocrMatch.get().score() < 0.62) {
            ocrMatch = Optional.empty();
        }
        if (templateMatch.isEmpty() && ocrMatch.isEmpty()) {
            return Optional.empty();
        }

        if (templateMatch.isPresent() && ocrMatch.isPresent()) {
            ImageHashTemplateMatcher.TemplateMatch template = templateMatch.get();
            OcrTitleMatcher.OcrMatch ocr = ocrMatch.get();
            if (template.card().gameCard().catalogId() == ocr.card().gameCard().catalogId()) {
                double confidence = clamp((0.15 * shape) + (0.55 * template.score()) + (0.30 * ocr.score()) + 0.05);
                return Optional.of(new ScoredCardMatch(template.card(), confidence, template.score(), ocr.score()));
            }

            if (Math.abs(template.score() - ocr.score()) < 0.18) {
                return Optional.empty();
            }
            if (template.score() > ocr.score()) {
                return Optional.of(new ScoredCardMatch(
                        template.card(),
                        clamp(((0.75 * template.score()) + (0.25 * shape)) * 0.72),
                        template.score(),
                        ocr.score()
                ));
            }
            return Optional.of(new ScoredCardMatch(
                    ocr.card(),
                    clamp(((0.75 * ocr.score()) + (0.25 * shape)) * 0.72),
                    template.score(),
                    ocr.score()
            ));
        }

        if (templateMatch.isPresent()) {
            ImageHashTemplateMatcher.TemplateMatch template = templateMatch.get();
            return Optional.of(new ScoredCardMatch(
                    template.card(),
                    clamp((0.80 * template.score()) + (0.20 * shape)),
                    template.score(),
                    0.0
            ));
        }

        OcrTitleMatcher.OcrMatch ocr = ocrMatch.orElseThrow();
        return Optional.of(new ScoredCardMatch(
                ocr.card(),
                clamp((0.78 * ocr.score()) + (0.22 * shape)),
                0.0,
                ocr.score()
        ));
    }

    private static double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    public record ScoredCardMatch(
            KnownGameCard card,
            double confidence,
            double templateScore,
            double ocrScore
    ) {
    }
}
