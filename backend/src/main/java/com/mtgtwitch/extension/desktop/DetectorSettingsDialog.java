package com.mtgtwitch.extension.desktop;

import com.mtgtwitch.extension.detection.vision.CaptureMode;
import com.mtgtwitch.extension.detection.vision.ScreenCalibration;

import javax.swing.BorderFactory;
import javax.swing.JCheckBox;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.IOException;
import java.nio.file.Path;

final class DetectorSettingsDialog {

    private DetectorSettingsDialog() {
    }

    static boolean show(Component parent, Path configPath) throws IOException {
        DetectorLocalSettings settings = DetectorLocalSettings.load(configPath);
        JCheckBox enabled = new JCheckBox("Enable experimental local screen detection", settings.enabled());
        JComboBox<CaptureMode> captureMode = new JComboBox<>(new CaptureMode[]{CaptureMode.SCREENSHOT, CaptureMode.OBS});
        captureMode.setSelectedItem(settings.captureMode() == CaptureMode.NONE ? CaptureMode.OBS : settings.captureMode());
        JSpinner x = decimalSpinner(settings.calibrationX());
        JSpinner y = decimalSpinner(settings.calibrationY());
        JSpinner w = decimalSpinner(settings.calibrationW());
        JSpinner h = decimalSpinner(settings.calibrationH());
        JButton selectArea = new JButton("Select screen area...");
        selectArea.addActionListener(event -> ScreenCalibrationPicker.select(parent).ifPresent(calibration -> {
            x.setValue(calibration.x());
            y.setValue(calibration.y());
            w.setValue(calibration.w());
            h.setValue(calibration.h());
        }));
        JTextField obsUrl = new JTextField(settings.obsUrl(), 28);
        JPasswordField obsPassword = new JPasswordField(settings.obsPassword(), 28);
        JTextField obsSource = new JTextField(settings.obsSourceName(), 28);
        JCheckBox templateEnabled = new JCheckBox("Match against known card art", settings.templateEnabled());
        JCheckBox ocrEnabled = new JCheckBox("Use Tesseract OCR as a secondary signal", settings.ocrEnabled());
        JTextField ocrExecutable = new JTextField(settings.ocrExecutable(), 28);
        JSpinner confidence = new JSpinner(new SpinnerNumberModel(settings.minConfidence(), 0.0, 1.0, 0.01));
        JSpinner scanSeconds = new JSpinner(new SpinnerNumberModel(settings.scanSeconds(), 2, 60, 1));

        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.insets = new Insets(4, 4, 4, 4);
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.weightx = 1;
        constraints.gridwidth = 2;
        constraints.gridx = 0;
        constraints.gridy = 0;
        panel.add(enabled, constraints);
        constraints.gridy++;
        panel.add(new JLabel("<html>Disabled by default. OBS mode scans the program scene. Screenshot mode crops the virtual desktop using normalized coordinates.</html>"), constraints);
        constraints.gridwidth = 1;
        addRow(panel, constraints, "Capture source", captureMode);
        addRow(panel, constraints, "Crop X (0-1)", x);
        addRow(panel, constraints, "Crop Y (0-1)", y);
        addRow(panel, constraints, "Crop width (0-1)", w);
        addRow(panel, constraints, "Crop height (0-1)", h);
        addRow(panel, constraints, "Visual calibration", selectArea);
        addRow(panel, constraints, "OBS WebSocket URL", obsUrl);
        addRow(panel, constraints, "OBS password", obsPassword);
        addRow(panel, constraints, "OBS scene/source (blank = program)", obsSource);
        constraints.gridx = 0;
        constraints.gridwidth = 2;
        constraints.gridy++;
        panel.add(templateEnabled, constraints);
        constraints.gridy++;
        panel.add(ocrEnabled, constraints);
        constraints.gridwidth = 1;
        addRow(panel, constraints, "Tesseract executable", ocrExecutable);
        addRow(panel, constraints, "Minimum confidence", confidence);
        addRow(panel, constraints, "Scan every (seconds)", scanSeconds);

        int result = JOptionPane.showConfirmDialog(
                parent,
                panel,
                "Experimental Screen Detector",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE
        );
        if (result != JOptionPane.OK_OPTION) {
            return false;
        }

        DetectorLocalSettings updated = new DetectorLocalSettings(
                true,
                enabled.isSelected(),
                (CaptureMode) captureMode.getSelectedItem(),
                number(x),
                number(y),
                number(w),
                number(h),
                obsUrl.getText(),
                new String(obsPassword.getPassword()),
                obsSource.getText(),
                ocrEnabled.isSelected(),
                ocrExecutable.getText(),
                templateEnabled.isSelected(),
                number(confidence),
                ((Number) scanSeconds.getValue()).intValue()
        );
        updated.save(configPath);
        return true;
    }

    private static JSpinner decimalSpinner(double value) {
        return new JSpinner(new SpinnerNumberModel(value, 0.0, 1.0, 0.01));
    }

    private static double number(JSpinner spinner) {
        return ((Number) spinner.getValue()).doubleValue();
    }

    private static void addRow(JPanel panel, GridBagConstraints constraints, String label, Component component) {
        constraints.gridy++;
        constraints.gridx = 0;
        constraints.weightx = 0;
        panel.add(new JLabel(label + ":"), constraints);
        constraints.gridx = 1;
        constraints.weightx = 1;
        panel.add(component, constraints);
    }
}
