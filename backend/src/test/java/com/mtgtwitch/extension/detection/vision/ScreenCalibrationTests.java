package com.mtgtwitch.extension.detection.vision;

import com.mtgtwitch.extension.detection.DetectionBbox;
import org.junit.jupiter.api.Test;

import java.awt.Rectangle;

import static org.assertj.core.api.Assertions.assertThat;

class ScreenCalibrationTests {

    @Test
    void mapsNormalizedCalibrationAcrossVirtualDesktopCoordinates() {
        ScreenCalibration calibration = new ScreenCalibration(0.5, 0.25, 0.5, 0.5);

        Rectangle bounds = calibration.toPixelBounds(new Rectangle(-1920, 0, 3840, 1080));

        assertThat(bounds).isEqualTo(new Rectangle(0, 270, 1920, 540));
    }

    @Test
    void clampsCalibrationToTheNormalizedFrame() {
        ScreenCalibration calibration = new ScreenCalibration(0.9, 0.8, 0.5, 0.5);

        assertThat(calibration.x()).isEqualTo(0.9);
        assertThat(calibration.y()).isEqualTo(0.8);
        assertThat(calibration.w()).isCloseTo(0.1, org.assertj.core.data.Offset.offset(0.000001));
        assertThat(calibration.h()).isCloseTo(0.2, org.assertj.core.data.Offset.offset(0.000001));
    }

    @Test
    void mapsCropLocalBoxBackIntoParentCoordinateSpace() {
        ScreenCalibration calibration = new ScreenCalibration(0.25, 0.10, 0.50, 0.80);

        DetectionBbox mapped = calibration.mapToParent(new DetectionBbox(0.20, 0.25, 0.40, 0.50));

        assertThat(mapped.x()).isCloseTo(0.35, org.assertj.core.data.Offset.offset(0.000001));
        assertThat(mapped.y()).isCloseTo(0.30, org.assertj.core.data.Offset.offset(0.000001));
        assertThat(mapped.w()).isCloseTo(0.20, org.assertj.core.data.Offset.offset(0.000001));
        assertThat(mapped.h()).isCloseTo(0.40, org.assertj.core.data.Offset.offset(0.000001));
    }
}
