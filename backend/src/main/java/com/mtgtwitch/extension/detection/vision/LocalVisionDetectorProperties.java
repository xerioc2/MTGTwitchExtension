package com.mtgtwitch.extension.detection.vision;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public final class LocalVisionDetectorProperties {

    private final CaptureMode captureMode;
    private final int maxWidth;
    private final ScreenCalibration calibration;
    private final String obsUrl;
    private final String obsPassword;
    private final String obsSourceName;
    private final double minArea;
    private final double maxArea;
    private final int maxRegions;
    private final boolean ocrEnabled;
    private final String ocrExecutable;
    private final boolean templateEnabled;
    private final double minConfidence;

    public LocalVisionDetectorProperties(
            @Value("${screen-detections.detector.capture.mode:none}") String captureMode,
            @Value("${screen-detections.detector.capture.max-width:1280}") int maxWidth,
            @Value("${screen-detections.detector.calibration.x:0.0}") double calibrationX,
            @Value("${screen-detections.detector.calibration.y:0.0}") double calibrationY,
            @Value("${screen-detections.detector.calibration.w:1.0}") double calibrationW,
            @Value("${screen-detections.detector.calibration.h:1.0}") double calibrationH,
            @Value("${screen-detections.detector.obs.url:ws://127.0.0.1:4455}") String obsUrl,
            @Value("${screen-detections.detector.obs.password:}") String obsPassword,
            @Value("${screen-detections.detector.obs.source-name:}") String obsSourceName,
            @Value("${screen-detections.detector.opencv.min-area:0.0025}") double minArea,
            @Value("${screen-detections.detector.opencv.max-area:0.25}") double maxArea,
            @Value("${screen-detections.detector.opencv.max-regions:20}") int maxRegions,
            @Value("${screen-detections.detector.ocr.enabled:false}") boolean ocrEnabled,
            @Value("${screen-detections.detector.ocr.executable:tesseract}") String ocrExecutable,
            @Value("${screen-detections.detector.template.enabled:true}") boolean templateEnabled,
            @Value("${screen-detections.detector.min-confidence:0.72}") double minConfidence
    ) {
        this.captureMode = CaptureMode.fromConfig(captureMode);
        this.maxWidth = Math.max(320, Math.min(4096, maxWidth));
        this.calibration = new ScreenCalibration(calibrationX, calibrationY, calibrationW, calibrationH);
        this.obsUrl = obsUrl == null ? "" : obsUrl.trim();
        this.obsPassword = obsPassword == null ? "" : obsPassword;
        this.obsSourceName = obsSourceName == null ? "" : obsSourceName.trim();
        this.minArea = Math.max(0.0001, Math.min(1.0, minArea));
        this.maxArea = Math.max(this.minArea, Math.min(1.0, maxArea));
        this.maxRegions = Math.max(1, Math.min(100, maxRegions));
        this.ocrEnabled = ocrEnabled;
        this.ocrExecutable = ocrExecutable == null || ocrExecutable.isBlank() ? "tesseract" : ocrExecutable.trim();
        this.templateEnabled = templateEnabled;
        this.minConfidence = Math.max(0.0, Math.min(1.0, minConfidence));
    }

    public CaptureMode captureMode() { return captureMode; }
    public int maxWidth() { return maxWidth; }
    public ScreenCalibration calibration() { return calibration; }
    public String obsUrl() { return obsUrl; }
    public String obsPassword() { return obsPassword; }
    public String obsSourceName() { return obsSourceName; }
    public double minArea() { return minArea; }
    public double maxArea() { return maxArea; }
    public int maxRegions() { return maxRegions; }
    public boolean ocrEnabled() { return ocrEnabled; }
    public String ocrExecutable() { return ocrExecutable; }
    public boolean templateEnabled() { return templateEnabled; }
    public double minConfidence() { return minConfidence; }
}
