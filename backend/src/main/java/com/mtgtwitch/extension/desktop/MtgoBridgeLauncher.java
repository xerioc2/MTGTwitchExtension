package com.mtgtwitch.extension.desktop;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mtgtwitch.extension.MtgoTwitchExtensionApplication;
import com.mtgtwitch.extension.log.LogWatchStatus;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.AWTException;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Image;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.Window;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.event.WindowStateListener;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class MtgoBridgeLauncher {

    private static final String WINDOW_TITLE = "MTGO Twitch Bridge";
    private static final String MESSAGE = "Open MTGO first, then start the bridge. If MTGO updates or the log is not found, click Refresh Log.";
    private static final int DEFAULT_PORT = 8080;
    private static final int MAX_PORT = 8090;
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter
            .ofPattern("MMM d, h:mm:ss a")
            .withZone(ZoneId.systemDefault());

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private JFrame frame;
    private JLabel backendStatusValue;
    private JLabel logStatusValue;
    private JLabel logPathValue;
    private JLabel websocketValue;
    private JLabel lastActivityValue;
    private JButton refreshButton;
    private JButton stopButton;
    private TrayIcon trayIcon;
    private Timer statusTimer;
    private ConfigurableApplicationContext applicationContext;
    private int serverPort = DEFAULT_PORT;
    private String websocketUrl = websocketUrl(DEFAULT_PORT);
    private final AtomicBoolean shutdownStarted = new AtomicBoolean(false);
    private volatile LogWatchStatus latestStatus = new LogWatchStatus(null, false, "Starting...");

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new MtgoBridgeLauncher().start(args));
    }

    private void start(String[] args) {
        Optional<Integer> availablePort = findAvailablePort();
        if (availablePort.isEmpty()) {
            JOptionPane.showMessageDialog(
                    null,
                    "No available ports found between 8080-8090. Please close other applications and try again.",
                    WINDOW_TITLE,
                    JOptionPane.ERROR_MESSAGE
            );
            System.exit(0);
            return;
        }

        serverPort = availablePort.get();
        websocketUrl = websocketUrl(serverPort);

        createWindow();
        setupTray();
        frame.setVisible(true);
        startSpringBoot(withServerPort(args, serverPort));
    }

    private void createWindow() {
        frame = new JFrame(WINDOW_TITLE);
        frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        frame.setSize(520, 300);
        frame.setMinimumSize(new Dimension(480, 280));
        frame.setLocationRelativeTo(null);

        JPanel root = new JPanel(new BorderLayout(12, 12));
        root.setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));

        JLabel message = new JLabel("<html>%s</html>".formatted(MESSAGE));
        message.setFont(message.getFont().deriveFont(Font.PLAIN, 13f));
        root.add(message, BorderLayout.NORTH);

        JPanel statusPanel = new JPanel(new GridBagLayout());
        statusPanel.setBorder(BorderFactory.createTitledBorder("Status"));
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.anchor = GridBagConstraints.WEST;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.weightx = 1;
        constraints.gridy = 0;

        backendStatusValue = addStatusRow(statusPanel, constraints, "Backend:", "Starting...");
        logStatusValue = addStatusRow(statusPanel, constraints, "MTGO log:", "Scanning...");
        logPathValue = addStatusRow(statusPanel, constraints, "Current log path:", "Not resolved yet");
        websocketValue = addStatusRow(statusPanel, constraints, "WebSocket URL:", websocketUrl);
        lastActivityValue = addStatusRow(statusPanel, constraints, "Last log activity:", "Unknown");

        root.add(statusPanel, BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        refreshButton = new JButton("Refresh Log");
        refreshButton.addActionListener(event -> refreshLog());
        stopButton = new JButton("Stop/Close");
        stopButton.addActionListener(event -> exitApplication());
        actions.add(refreshButton);
        actions.add(stopButton);
        root.add(actions, BorderLayout.SOUTH);

        frame.setContentPane(root);
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent event) {
                exitApplication();
            }
        });
        WindowStateListener minimizeListener = event -> {
            if ((event.getNewState() & JFrame.ICONIFIED) == JFrame.ICONIFIED) {
                minimizeToTray();
            }
        };
        frame.addWindowStateListener(minimizeListener);
    }

    private JLabel addStatusRow(JPanel panel, GridBagConstraints constraints, String label, String initialValue) {
        constraints.gridx = 0;
        constraints.weightx = 0;
        JLabel nameLabel = new JLabel(label);
        nameLabel.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 12));
        panel.add(nameLabel, constraints);

        constraints.gridx = 1;
        constraints.weightx = 1;
        JLabel valueLabel = new JLabel(initialValue);
        valueLabel.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        panel.add(valueLabel, constraints);
        constraints.gridy++;

        return valueLabel;
    }

    private void setupTray() {
        if (!SystemTray.isSupported()) {
            return;
        }

        PopupMenu menu = new PopupMenu();
        MenuItem open = new MenuItem("Open");
        open.addActionListener(event -> showWindow());
        MenuItem refresh = new MenuItem("Refresh Log");
        refresh.addActionListener(event -> refreshLog());
        MenuItem exit = new MenuItem("Exit");
        exit.addActionListener(event -> exitApplication());
        menu.add(open);
        menu.add(refresh);
        menu.addSeparator();
        menu.add(exit);

        trayIcon = new TrayIcon(createTrayImage(false), WINDOW_TITLE, menu);
        trayIcon.setImageAutoSize(true);
        trayIcon.addActionListener(event -> showWindow());

        try {
            SystemTray.getSystemTray().add(trayIcon);
        } catch (AWTException exception) {
            trayIcon = null;
        }
    }

    private void startSpringBoot(String[] args) {
        setControlsEnabled(false);
        CompletableFuture.runAsync(() -> {
            try {
                Thread.currentThread().setContextClassLoader(MtgoTwitchExtensionApplication.class.getClassLoader());
                applicationContext = new SpringApplicationBuilder(MtgoTwitchExtensionApplication.class)
                        .headless(false)
                        .run(args);
                recordBoundPort();
                registerShutdownHook();
                SwingUtilities.invokeLater(() -> {
                    backendStatusValue.setText("Running");
                    setControlsEnabled(true);
                    startStatusTimer();
                    refreshLog();
                });
            } catch (Throwable exception) {
                exception.printStackTrace();
                SwingUtilities.invokeLater(() -> {
                    backendStatusValue.setText("Stopped");
                    logStatusValue.setText("Not found");
                    logPathValue.setText("Backend failed to start: " + exception.getMessage());
                    setControlsEnabled(true);
                    updateTray(false);
                    JOptionPane.showMessageDialog(
                            frame,
                            "Spring Boot failed to start:\n\n" + exception.getClass().getName() + "\n" + exception.getMessage(),
                            WINDOW_TITLE,
                            JOptionPane.ERROR_MESSAGE
                    );
                });
            }
        });
    }

    private void refreshLog() {
        setControlsEnabled(false);
        HttpRequest request = HttpRequest.newBuilder(rescanUri(serverPort))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();

        httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(this::parseStatus)
                .exceptionally(exception -> new LogWatchStatus(null, false, "Unable to contact backend: " + exception.getMessage()))
                .thenAccept(status -> SwingUtilities.invokeLater(() -> {
                    latestStatus = status;
                    updateStatus(status);
                    setControlsEnabled(true);
                }));
    }

    private LogWatchStatus parseStatus(HttpResponse<String> response) {
        try {
            return objectMapper.readValue(response.body(), LogWatchStatus.class);
        } catch (IOException exception) {
            return new LogWatchStatus(null, false, "Unexpected backend response: " + response.statusCode());
        }
    }

    private void updateStatus(LogWatchStatus status) {
        boolean backendRunning = applicationContext != null && applicationContext.isActive();
        backendStatusValue.setText(backendRunning ? "Running" : "Stopped");
        logStatusValue.setText(status.watching() ? "Found" : "Not found");
        logPathValue.setText(status.path() == null ? status.message() : status.path());
        websocketValue.setText(websocketUrl);
        lastActivityValue.setText(resolveLastActivity(status.path()));
        updateTray(backendRunning && status.watching());
    }

    private String resolveLastActivity(String pathText) {
        if (pathText == null || pathText.isBlank()) {
            return "Unknown";
        }

        try {
            Path path = Path.of(pathText);
            if (!Files.exists(path)) {
                return "Unknown";
            }
            Instant modifiedAt = Files.getLastModifiedTime(path).toInstant();
            return TIME_FORMATTER.format(modifiedAt);
        } catch (Exception exception) {
            return "Unknown";
        }
    }

    private void startStatusTimer() {
        if (statusTimer != null) {
            statusTimer.stop();
        }

        statusTimer = new Timer(5000, event -> updateStatus(latestStatus));
        statusTimer.start();
    }

    private void recordBoundPort() {
        String localPort = applicationContext.getEnvironment().getProperty("local.server.port");
        String configuredPort = applicationContext.getEnvironment().getProperty("server.port");
        serverPort = parsePort(localPort)
                .or(() -> parsePort(configuredPort))
                .orElse(serverPort);
        websocketUrl = websocketUrl(serverPort);
    }

    private Optional<Integer> findAvailablePort() {
        for (int port = DEFAULT_PORT; port <= MAX_PORT; port++) {
            if (!isPortBound(port)) {
                return Optional.of(port);
            }
        }

        return Optional.empty();
    }

    private String[] withServerPort(String[] args, int port) {
        String[] filteredArgs = java.util.Arrays.stream(args)
                .filter(arg -> !arg.startsWith("--server.port="))
                .toArray(String[]::new);
        String[] result = java.util.Arrays.copyOf(filteredArgs, filteredArgs.length + 1);
        result[filteredArgs.length] = "--server.port=" + port;
        return result;
    }

    private Optional<Integer> parsePort(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }

        try {
            int port = Integer.parseInt(value.trim());
            if (port > 0 && port <= 65535) {
                return Optional.of(port);
            }
        } catch (NumberFormatException ignored) {
            return Optional.empty();
        }

        return Optional.empty();
    }

    private boolean isPortBound(int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("127.0.0.1", port), 250);
            return true;
        } catch (IOException exception) {
            return false;
        }
    }

    private void registerShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> shutdownBridge(false), "mtgo-bridge-shutdown"));
    }

    private static String websocketUrl(int port) {
        return "ws://localhost:%d/ws/game-state".formatted(port);
    }

    private static URI rescanUri(int port) {
        return URI.create("http://localhost:%d/api/rescan-log".formatted(port));
    }

    private void setControlsEnabled(boolean enabled) {
        if (refreshButton != null) {
            refreshButton.setEnabled(enabled);
        }
        if (stopButton != null) {
            stopButton.setEnabled(true);
        }
    }

    private void minimizeToTray() {
        if (trayIcon == null) {
            frame.setState(JFrame.ICONIFIED);
            return;
        }

        frame.setVisible(false);
        trayIcon.displayMessage(WINDOW_TITLE, "Bridge is still running in the system tray.", TrayIcon.MessageType.INFO);
    }

    private void showWindow() {
        frame.setVisible(true);
        frame.setState(JFrame.NORMAL);
        frame.toFront();
    }

    private void updateTray(boolean connected) {
        if (trayIcon != null) {
            trayIcon.setImage(createTrayImage(connected));
            trayIcon.setToolTip(WINDOW_TITLE + " - " + (connected ? "Connected" : "Not connected"));
        }
    }

    private Image createTrayImage(boolean connected) {
        int size = 16;
        java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(size, size, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(new Color(25, 25, 25, 0));
        graphics.fillRect(0, 0, size, size);
        graphics.setColor(connected ? new Color(53, 190, 115) : new Color(216, 70, 70));
        graphics.fillOval(2, 2, 12, 12);
        graphics.setColor(new Color(30, 30, 30));
        graphics.drawOval(2, 2, 12, 12);
        graphics.dispose();
        return image;
    }

    private void exitApplication() {
        shutdownBridge(true);
    }

    private void shutdownBridge(boolean exitJvm) {
        if (!shutdownStarted.compareAndSet(false, true)) {
            return;
        }

        if (statusTimer != null) {
            statusTimer.stop();
        }

        if (trayIcon != null) {
            SystemTray.getSystemTray().remove(trayIcon);
        }

        ConfigurableApplicationContext context = applicationContext;
        if (context != null) {
            closeContext(context);
            applicationContext = null;
        }

        if (isPortBound(serverPort)) {
            forceReleasePort(serverPort);
        }

        for (Window window : Window.getWindows()) {
            window.dispose();
        }

        if (exitJvm) {
            System.exit(0);
        }
    }

    private void closeContext(ConfigurableApplicationContext context) {
        CompletableFuture<Void> closeTask = CompletableFuture.runAsync(context::close);
        try {
            closeTask.get(3, TimeUnit.SECONDS);
        } catch (Exception exception) {
            closeTask.cancel(true);
        }
    }

    private void forceReleasePort(int port) {
        findProcessIdBoundToPort(port).ifPresent(processId -> {
            long currentProcessId = ProcessHandle.current().pid();
            if (processId == currentProcessId) {
                return;
            }

            try {
                new ProcessBuilder("taskkill", "/PID", Long.toString(processId), "/F")
                        .redirectErrorStream(true)
                        .start()
                        .waitFor(3, TimeUnit.SECONDS);
            } catch (Exception ignored) {
                // The JVM is already shutting down; best effort only.
            }
        });
    }

    private Optional<Long> findProcessIdBoundToPort(int port) {
        try {
            Process process = new ProcessBuilder("netstat", "-ano", "-p", "TCP")
                    .redirectErrorStream(true)
                    .start();
            String output = new String(process.getInputStream().readAllBytes());
            process.waitFor(3, TimeUnit.SECONDS);
            String portSuffix = ":" + port;
            for (String line : output.split("\\R")) {
                String trimmed = line.trim();
                if (!trimmed.startsWith("TCP") || !trimmed.contains("LISTENING")) {
                    continue;
                }

                String[] parts = trimmed.split("\\s+");
                if (parts.length < 5 || !parts[1].endsWith(portSuffix)) {
                    continue;
                }

                return parseProcessId(parts[4]);
            }
        } catch (Exception ignored) {
            return Optional.empty();
        }

        return Optional.empty();
    }

    private Optional<Long> parseProcessId(String value) {
        try {
            return Optional.of(Long.parseLong(value));
        } catch (NumberFormatException exception) {
            return Optional.empty();
        }
    }
}
