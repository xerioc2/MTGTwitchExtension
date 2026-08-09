package com.mtgtwitch.extension.detection.vision;

import com.mtgtwitch.extension.detection.DetectionBbox;

public record DetectedCardCandidate(DetectionBbox bbox, double shapeConfidence) {
}
