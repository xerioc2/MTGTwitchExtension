package com.mtgtwitch.extension.detection.vision;

import com.mtgtwitch.extension.detection.DetectionBbox;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

public final class FrameImages {

    private FrameImages() {
    }

    public static BufferedImage scaleToMaxWidth(BufferedImage image, int maxWidth) {
        if (image.getWidth() <= maxWidth) {
            return image;
        }
        int height = Math.max(1, (int) Math.round(image.getHeight() * (maxWidth / (double) image.getWidth())));
        BufferedImage scaled = new BufferedImage(maxWidth, height, BufferedImage.TYPE_3BYTE_BGR);
        Graphics2D graphics = scaled.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        graphics.drawImage(image, 0, 0, maxWidth, height, null);
        graphics.dispose();
        return scaled;
    }

    public static BufferedImage crop(BufferedImage image, DetectionBbox bbox) {
        int x = Math.max(0, (int) Math.floor(bbox.x() * image.getWidth()));
        int y = Math.max(0, (int) Math.floor(bbox.y() * image.getHeight()));
        int right = Math.min(image.getWidth(), (int) Math.ceil((bbox.x() + bbox.w()) * image.getWidth()));
        int bottom = Math.min(image.getHeight(), (int) Math.ceil((bbox.y() + bbox.h()) * image.getHeight()));
        if (right <= x || bottom <= y) {
            throw new IllegalArgumentException("Bounding box has no image area.");
        }
        return image.getSubimage(x, y, right - x, bottom - y);
    }

    public static BufferedImage normalizeCardOrientation(BufferedImage image) {
        if (image.getHeight() >= image.getWidth()) {
            return image;
        }
        BufferedImage rotated = new BufferedImage(image.getHeight(), image.getWidth(), image.getType());
        Graphics2D graphics = rotated.createGraphics();
        graphics.translate(rotated.getWidth(), 0);
        graphics.rotate(Math.PI / 2.0);
        graphics.drawImage(image, 0, 0, null);
        graphics.dispose();
        return rotated;
    }
}
