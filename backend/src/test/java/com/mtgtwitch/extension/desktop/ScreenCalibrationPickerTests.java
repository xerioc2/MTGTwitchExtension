package com.mtgtwitch.extension.desktop;

import com.mtgtwitch.extension.detection.vision.ScreenCalibration;
import org.junit.jupiter.api.Test;

import java.awt.Dimension;
import java.awt.Rectangle;

import static org.assertj.core.api.Assertions.assertThat;

class ScreenCalibrationPickerTests {

    @Test
    void convertsDragSelectionToNormalizedCalibration() {
        ScreenCalibration calibration = ScreenCalibrationPicker.toCalibration(
                new Rectangle(100, 50, 800, 450),
                new Dimension(1000, 500)
        );

        assertThat(calibration.x()).isEqualTo(0.1);
        assertThat(calibration.y()).isEqualTo(0.1);
        assertThat(calibration.w()).isEqualTo(0.8);
        assertThat(calibration.h()).isEqualTo(0.9);
    }
}
