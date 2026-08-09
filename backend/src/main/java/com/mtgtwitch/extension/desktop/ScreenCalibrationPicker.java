package com.mtgtwitch.extension.desktop;

import com.mtgtwitch.extension.detection.vision.ScreenCalibration;
import com.mtgtwitch.extension.detection.vision.ScreenshotFrameSource;

import javax.swing.BorderFactory;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Robot;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.Optional;

final class ScreenCalibrationPicker {

    private ScreenCalibrationPicker() {
    }

    static Optional<ScreenCalibration> select(Component parent) {
        try {
            Rectangle desktopBounds = ScreenshotFrameSource.virtualDesktopBounds();
            BufferedImage screenshot = new Robot().createScreenCapture(desktopBounds);
            SelectionPanel selectionPanel = new SelectionPanel(screenshot, 1080, 680);
            int result = JOptionPane.showConfirmDialog(
                    parent,
                    selectionPanel,
                    "Drag around the broadcast/game area",
                    JOptionPane.OK_CANCEL_OPTION,
                    JOptionPane.PLAIN_MESSAGE
            );
            if (result != JOptionPane.OK_OPTION || selectionPanel.selection() == null) {
                return Optional.empty();
            }
            return Optional.of(toCalibration(selectionPanel.selection(), selectionPanel.getPreferredSize()));
        } catch (Exception exception) {
            JOptionPane.showMessageDialog(
                    parent,
                    "Could not capture the desktop for calibration:\n\n" + exception.getMessage(),
                    "Experimental Screen Detector",
                    JOptionPane.WARNING_MESSAGE
            );
            return Optional.empty();
        }
    }

    static ScreenCalibration toCalibration(Rectangle selection, Dimension previewSize) {
        if (selection == null || previewSize == null || previewSize.width <= 0 || previewSize.height <= 0) {
            throw new IllegalArgumentException("A selection and preview dimensions are required.");
        }
        return new ScreenCalibration(
                selection.x / (double) previewSize.width,
                selection.y / (double) previewSize.height,
                selection.width / (double) previewSize.width,
                selection.height / (double) previewSize.height
        );
    }

    private static final class SelectionPanel extends JPanel {

        private final BufferedImage screenshot;
        private Point start;
        private Rectangle selection;

        private SelectionPanel(BufferedImage screenshot, int maximumWidth, int maximumHeight) {
            this.screenshot = screenshot;
            double scale = Math.min(
                    1.0,
                    Math.min(maximumWidth / (double) screenshot.getWidth(), maximumHeight / (double) screenshot.getHeight())
            );
            setPreferredSize(new Dimension(
                    Math.max(1, (int) Math.round(screenshot.getWidth() * scale)),
                    Math.max(1, (int) Math.round(screenshot.getHeight() * scale))
            ));
            setBorder(BorderFactory.createLineBorder(Color.DARK_GRAY));
            MouseAdapter mouse = new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent event) {
                    start = clamp(event.getPoint());
                    selection = new Rectangle(start);
                    repaint();
                }

                @Override
                public void mouseDragged(MouseEvent event) {
                    if (start != null) {
                        selection = rectangleBetween(start, clamp(event.getPoint()));
                        repaint();
                    }
                }

                @Override
                public void mouseReleased(MouseEvent event) {
                    if (start != null) {
                        selection = rectangleBetween(start, clamp(event.getPoint()));
                        start = null;
                        repaint();
                    }
                }
            };
            addMouseListener(mouse);
            addMouseMotionListener(mouse);
        }

        Rectangle selection() {
            return selection == null || selection.width < 2 || selection.height < 2 ? null : selection;
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D graphics2D = (Graphics2D) graphics.create();
            graphics2D.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics2D.drawImage(screenshot, 0, 0, getWidth(), getHeight(), null);
            if (selection != null) {
                graphics2D.setColor(new Color(0, 0, 0, 90));
                graphics2D.fillRect(0, 0, getWidth(), selection.y);
                graphics2D.fillRect(0, selection.y, selection.x, selection.height);
                graphics2D.fillRect(selection.x + selection.width, selection.y,
                        getWidth() - selection.x - selection.width, selection.height);
                graphics2D.fillRect(0, selection.y + selection.height,
                        getWidth(), getHeight() - selection.y - selection.height);
                graphics2D.setColor(new Color(72, 220, 137));
                graphics2D.setStroke(new BasicStroke(2f));
                graphics2D.draw(selection);
            }
            graphics2D.dispose();
        }

        private Point clamp(Point point) {
            return new Point(
                    Math.max(0, Math.min(getPreferredSize().width, point.x)),
                    Math.max(0, Math.min(getPreferredSize().height, point.y))
            );
        }

        private static Rectangle rectangleBetween(Point first, Point second) {
            return new Rectangle(
                    Math.min(first.x, second.x),
                    Math.min(first.y, second.y),
                    Math.abs(first.x - second.x),
                    Math.abs(first.y - second.y)
            );
        }
    }
}
