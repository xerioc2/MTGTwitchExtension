package com.mtgtwitch.extension.detection.vision;

import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;

import static org.assertj.core.api.Assertions.assertThat;

class FrameImagesTests {

    @Test
    void rotatesLandscapeCardCropToPortraitForMatching() {
        BufferedImage landscape = new BufferedImage(176, 126, BufferedImage.TYPE_INT_RGB);

        BufferedImage normalized = FrameImages.normalizeCardOrientation(landscape);

        assertThat(normalized.getWidth()).isEqualTo(126);
        assertThat(normalized.getHeight()).isEqualTo(176);
    }

    @Test
    void leavesPortraitCardCropUnchanged() {
        BufferedImage portrait = new BufferedImage(126, 176, BufferedImage.TYPE_INT_RGB);

        assertThat(FrameImages.normalizeCardOrientation(portrait)).isSameAs(portrait);
    }
}
