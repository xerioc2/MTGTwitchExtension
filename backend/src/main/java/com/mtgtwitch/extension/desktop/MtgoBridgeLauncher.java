package com.mtgtwitch.extension.desktop;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mtgtwitch.extension.MtgoTwitchExtensionApplication;
import com.mtgtwitch.extension.log.LogWatchStatus;
import com.mtgtwitch.extension.relay.SupabaseRelayPublisher;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.AWTException;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Desktop;
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
import java.io.InputStream;
import java.net.BindException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.imageio.ImageIO;

public class MtgoBridgeLauncher {

    private static final String WINDOW_TITLE = "MTGO Twitch Bridge";
    private static final String APP_ICON_RESOURCE = "/icons/bridge-icon.png";
    private static final String MESSAGE = "Open MTGO first, then start the bridge. If MTGO updates or the log is not found, click Refresh Log.";
    private static final int DEFAULT_PORT = 8080;
    private static final int MAX_PORT = 8090;
    private static final int TWITCH_CALLBACK_PORT = 8787;
    private static final String TWITCH_CALLBACK_PATH = "/callback";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final Path CONFIG_PATH = Path.of(
            System.getenv("APPDATA") == null ? System.getProperty("user.home") : System.getenv("APPDATA"),
            "MTGO Twitch Bridge",
            "config.properties"
    );
    private static final String TWITCH_LOGIN_KEY = "twitch.login";
    private static final String CHANNEL_ID_KEY = "supabase.channel.id";
    private static final String RELAY_FUNCTION_URL_KEY = "supabase.relay.function.url";
    private static final String BRIDGE_PUBLISH_TOKEN_KEY = "bridge.publish.token";
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
    private JLabel authStatusValue;
    private JButton loginButton;
    private JButton logoutButton;
    private JButton refreshButton;
    private JButton stopButton;
    private JCheckBox rememberLoginCheckbox;
    private MenuItem logoutMenuItem;
    private TrayIcon trayIcon;
    private Image appIconImage;
    private Timer statusTimer;
    private ConfigurableApplicationContext applicationContext;
    private int serverPort = DEFAULT_PORT;
    private String websocketUrl = websocketUrl(DEFAULT_PORT);
    private final AtomicBoolean shutdownStarted = new AtomicBoolean(false);
    private volatile LogWatchStatus latestStatus = new LogWatchStatus(null, false, "Starting...");
    private volatile BridgeConfig bridgeConfig;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new MtgoBridgeLauncher().start(args));
    }

    private void start(String[] args) {
        bridgeConfig = loadBridgeConfig().orElse(null);
        appIconImage = loadAppIcon();

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
        updateAuthUi();
        setupTray();
        updateAuthUi();
        frame.setVisible(true);
        startSpringBoot(withServerPort(args, serverPort));
    }

    private void createWindow() {
        frame = new JFrame(WINDOW_TITLE);
        if (appIconImage != null) {
            frame.setIconImage(appIconImage);
        }
        frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        frame.setSize(560, 340);
        frame.setMinimumSize(new Dimension(520, 320));
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
        authStatusValue = addStatusRow(statusPanel, constraints, "Twitch channel:", "Not logged in");

        root.add(statusPanel, BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        rememberLoginCheckbox = new JCheckBox("Remember login", true);
        loginButton = new JButton("Login with Twitch");
        loginButton.addActionListener(event -> loginWithTwitch());
        logoutButton = new JButton("Log out");
        logoutButton.addActionListener(event -> logout());
        refreshButton = new JButton("Refresh Log");
        refreshButton.addActionListener(event -> refreshLog());
        stopButton = new JButton("Stop/Close");
        stopButton.addActionListener(event -> exitApplication());
        actions.add(rememberLoginCheckbox);
        actions.add(loginButton);
        actions.add(logoutButton);
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
        logoutMenuItem = new MenuItem("Log out");
        logoutMenuItem.addActionListener(event -> logout());
        MenuItem exit = new MenuItem("Exit");
        exit.addActionListener(event -> exitApplication());
        menu.add(open);
        menu.add(refresh);
        menu.add(logoutMenuItem);
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
                applyBridgeConfigIfPresent();
                registerShutdownHook();
                SwingUtilities.invokeLater(() -> {
                    backendStatusValue.setText("Running");
                    updateAuthUi();
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

    private void loginWithTwitch() {
        setControlsEnabled(false);
        if (loginButton != null) {
            loginButton.setText("Logging in...");
        }

        CompletableFuture.runAsync(() -> {
            HttpServer callbackServer = null;
            try {
                BridgeAuthProperties authProperties = applicationContext.getBean(BridgeAuthProperties.class);
                if (isBlank(authProperties.twitchClientId())) {
                    throw new IllegalStateException("TWITCH_CLIENT_ID must be configured before login.");
                }

                PkcePair pkcePair = createPkcePair();
                String state = randomHex(16);
                CompletableFuture<String> authorizationCode = new CompletableFuture<>();
                callbackServer = HttpServer.create(new InetSocketAddress("127.0.0.1", TWITCH_CALLBACK_PORT), 0);
                int callbackPort = callbackServer.getAddress().getPort();
                URI redirectUri = URI.create("http://localhost:%d%s".formatted(callbackPort, TWITCH_CALLBACK_PATH));
                HttpServer server = callbackServer;
                callbackServer.createContext(TWITCH_CALLBACK_PATH, exchange -> handleTwitchCallback(exchange, authorizationCode, state));
                callbackServer.start();

                openBrowser(twitchAuthorizeUri(authProperties.twitchClientId(), redirectUri, pkcePair.codeChallenge(), state));

                String code = authorizationCode.get(60, TimeUnit.SECONDS);
                server.stop(0);
                callbackServer = null;

                IssuedBridgeToken issuedToken = issueBridgeToken(
                        authProperties.issueBridgeTokenUrl(),
                        code,
                        pkcePair.codeVerifier(),
                        redirectUri.toString()
                );
                BridgeConfig config = new BridgeConfig(
                        issuedToken.channelId(),
                        issuedToken.channelId(),
                        issuedToken.relayFunctionUrl(),
                        issuedToken.bridgeToken()
                );
                boolean rememberLogin = rememberLoginCheckbox == null || rememberLoginCheckbox.isSelected();
                if (rememberLogin) {
                    saveBridgeConfig(config);
                } else {
                    deleteBridgeConfig();
                }
                bridgeConfig = config;
                configurePublisher(config);

                SwingUtilities.invokeLater(() -> {
                    updateAuthUi();
                    setControlsEnabled(true);
                    JOptionPane.showMessageDialog(
                            frame,
                            "Logged in as " + config.twitchLogin() + (rememberLogin ? "" : "\n\nThis login will be forgotten when the bridge closes."),
                            WINDOW_TITLE,
                            JOptionPane.INFORMATION_MESSAGE
                    );
                });
            } catch (BindException exception) {
                if (callbackServer != null) {
                    callbackServer.stop(0);
                }
                SwingUtilities.invokeLater(() -> {
                    updateAuthUi();
                    setControlsEnabled(true);
                    JOptionPane.showMessageDialog(
                            frame,
                            "Twitch login could not start because localhost port %d is already in use.\n\nClose the other app using that port and try again.\n\nRegistered redirect URL:\nhttp://localhost:%d%s".formatted(
                                    TWITCH_CALLBACK_PORT,
                                    TWITCH_CALLBACK_PORT,
                                    TWITCH_CALLBACK_PATH
                            ),
                            WINDOW_TITLE,
                            JOptionPane.ERROR_MESSAGE
                    );
                });
            } catch (Exception exception) {
                if (callbackServer != null) {
                    callbackServer.stop(0);
                }
                SwingUtilities.invokeLater(() -> {
                    updateAuthUi();
                    setControlsEnabled(true);
                    JOptionPane.showMessageDialog(
                            frame,
                            "Twitch login failed:\n\n" + exception.getMessage(),
                            WINDOW_TITLE,
                            JOptionPane.ERROR_MESSAGE
                    );
                });
            }
        });
    }

    private void handleTwitchCallback(HttpExchange exchange, CompletableFuture<String> authorizationCode, String expectedState) throws IOException {
        Map<String, String> query = parseQuery(exchange.getRequestURI().getRawQuery());
        String code = query.get("code");
        String error = query.get("error");
        String state = query.get("state");

        String responseText;
        if (!expectedState.equals(state)) {
            authorizationCode.completeExceptionally(new IllegalStateException("Twitch login state did not match."));
            responseText = """
                    <!doctype html><html lang="en"><head><meta charset="utf-8">
                    <title>MTGO Twitch Bridge</title>
                    <style>body{margin:0;display:flex;align-items:center;justify-content:center;min-height:100vh;
                    font-family:sans-serif;background:#0e0e10;color:#efeff1}
                    .box{text-align:center;padding:40px}h1{margin:0 0 12px;font-size:1.4rem;color:#f04747}
                    p{margin:0;color:#adadb8;font-size:0.95rem}</style></head>
                    <body><div class="box"><h1>Login failed</h1>
                    <p>You can close this window. Check the bridge window for details.</p>
                    </div></body></html>""";
        } else if (!isBlank(code)) {
            authorizationCode.complete(code);
            responseText = """
                    <!doctype html><html lang="en"><head><meta charset="utf-8">
                    <title>MTGO Twitch Bridge</title>
                    <style>body{margin:0;display:flex;align-items:center;justify-content:center;min-height:100vh;
                    font-family:sans-serif;background:#0e0e10;color:#efeff1}
                    .box{text-align:center;padding:40px}h1{margin:0 0 12px;font-size:1.4rem;color:#a970ff}
                    p{margin:0;color:#adadb8;font-size:0.95rem}</style></head>
                    <body><div class="box"><h1>Login complete</h1>
                    <p>You can close this window and return to the MTGO Twitch Bridge.</p>
                    </div></body></html>""";
        } else {
            authorizationCode.completeExceptionally(new IllegalStateException(isBlank(error) ? "Twitch did not return an authorization code." : error));
            responseText = """
                    <!doctype html><html lang="en"><head><meta charset="utf-8">
                    <title>MTGO Twitch Bridge</title>
                    <style>body{margin:0;display:flex;align-items:center;justify-content:center;min-height:100vh;
                    font-family:sans-serif;background:#0e0e10;color:#efeff1}
                    .box{text-align:center;padding:40px}h1{margin:0 0 12px;font-size:1.4rem;color:#f04747}
                    p{margin:0;color:#adadb8;font-size:0.95rem}</style></head>
                    <body><div class="box"><h1>Login failed</h1>
                    <p>You can close this window. Check the bridge window for details.</p>
                    </div></body></html>""";
        }

        byte[] responseBytes = responseText.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
        exchange.sendResponseHeaders(200, responseBytes.length);
        exchange.getResponseBody().write(responseBytes);
        exchange.close();
    }

    private URI twitchAuthorizeUri(String clientId, URI redirectUri, String codeChallenge, String state) {
        return URI.create("https://id.twitch.tv/oauth2/authorize?client_id=%s&redirect_uri=%s&response_type=code&scope=user:read:email&code_challenge=%s&code_challenge_method=S256&state=%s".formatted(
                urlEncode(clientId),
                urlEncode(redirectUri.toString()),
                urlEncode(codeChallenge),
                urlEncode(state)
        ));
    }

    private PkcePair createPkcePair() throws NoSuchAlgorithmException {
        byte[] verifierBytes = new byte[32];
        SECURE_RANDOM.nextBytes(verifierBytes);
        String codeVerifier = Base64.getUrlEncoder().withoutPadding().encodeToString(verifierBytes);

        byte[] hash = MessageDigest.getInstance("SHA-256")
                .digest(codeVerifier.getBytes(StandardCharsets.US_ASCII));
        String codeChallenge = Base64.getUrlEncoder().withoutPadding().encodeToString(hash);

        return new PkcePair(codeVerifier, codeChallenge);
    }

    private String randomHex(int byteLength) {
        byte[] bytes = new byte[byteLength];
        SECURE_RANDOM.nextBytes(bytes);
        return bytesToHex(bytes);
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            builder.append(String.format("%02x", value));
        }
        return builder.toString();
    }

    private IssuedBridgeToken issueBridgeToken(String issueBridgeTokenUrl, String code, String codeVerifier, String redirectUri) throws IOException, InterruptedException {
        if (isBlank(issueBridgeTokenUrl)) {
            throw new IllegalStateException("SUPABASE_ISSUE_TOKEN_URL is not configured.");
        }

        String requestBody = objectMapper.writeValueAsString(new IssueBridgeTokenRequest(code, codeVerifier, redirectUri));
        HttpRequest request = HttpRequest.newBuilder(URI.create(issueBridgeTokenUrl))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("Bridge token issue failed with status " + response.statusCode());
        }

        return objectMapper.readValue(response.body(), IssuedBridgeToken.class);
    }

    private void openBrowser(URI uri) throws IOException {
        if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            throw new IllegalStateException("Desktop browser launch is not supported on this system.");
        }

        Desktop.getDesktop().browse(uri);
    }

    private Map<String, String> parseQuery(String rawQuery) {
        if (rawQuery == null || rawQuery.isBlank()) {
            return Map.of();
        }

        java.util.HashMap<String, String> values = new java.util.HashMap<>();
        for (String part : rawQuery.split("&")) {
            int separator = part.indexOf('=');
            String name = separator >= 0 ? part.substring(0, separator) : part;
            String value = separator >= 0 ? part.substring(separator + 1) : "";
            values.put(urlDecode(name), urlDecode(value));
        }

        return values;
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String urlDecode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private Optional<BridgeConfig> loadBridgeConfig() {
        if (!Files.exists(CONFIG_PATH)) {
            return Optional.empty();
        }

        Properties properties = new Properties();
        try (java.io.Reader reader = Files.newBufferedReader(CONFIG_PATH, StandardCharsets.UTF_8)) {
            properties.load(reader);
        } catch (IOException exception) {
            return Optional.empty();
        }

        BridgeConfig config = new BridgeConfig(
                properties.getProperty(TWITCH_LOGIN_KEY, ""),
                properties.getProperty(CHANNEL_ID_KEY, ""),
                properties.getProperty(RELAY_FUNCTION_URL_KEY, ""),
                properties.getProperty(BRIDGE_PUBLISH_TOKEN_KEY, "")
        );

        return config.isComplete() ? Optional.of(config) : Optional.empty();
    }

    private void saveBridgeConfig(BridgeConfig config) throws IOException {
        Files.createDirectories(CONFIG_PATH.getParent());
        Properties properties = new Properties();
        properties.setProperty(TWITCH_LOGIN_KEY, config.twitchLogin());
        properties.setProperty(CHANNEL_ID_KEY, config.channelId());
        properties.setProperty(RELAY_FUNCTION_URL_KEY, config.relayFunctionUrl());
        properties.setProperty(BRIDGE_PUBLISH_TOKEN_KEY, config.bridgePublishToken());

        try (java.io.Writer writer = Files.newBufferedWriter(CONFIG_PATH, StandardCharsets.UTF_8)) {
            properties.store(writer, "MTGO Twitch Bridge");
        }
    }

    private void applyBridgeConfigIfPresent() {
        if (bridgeConfig != null && bridgeConfig.isComplete()) {
            configurePublisher(bridgeConfig);
        }
    }

    private void configurePublisher(BridgeConfig config) {
        applicationContext.getBean(SupabaseRelayPublisher.class)
                .configure(config.channelId(), config.relayFunctionUrl(), config.bridgePublishToken());
    }

    private void logout() {
        BridgeConfig configToRevoke = bridgeConfig;
        revokeBridgeToken(configToRevoke);
        deleteBridgeConfig();

        bridgeConfig = null;
        ConfigurableApplicationContext context = applicationContext;
        if (context != null && context.isActive()) {
            context.getBean(SupabaseRelayPublisher.class).configure("", "", "");
        }
        updateAuthUi();
    }

    private void revokeBridgeToken(BridgeConfig config) {
        if (config == null || !config.isComplete() || isBlank(config.relayFunctionUrl())) {
            return;
        }

        CompletableFuture.runAsync(() -> {
            try {
                HttpRequest request = HttpRequest.newBuilder(URI.create(config.relayFunctionUrl()))
                        .header("Authorization", "Bearer " + config.bridgePublishToken())
                        .DELETE()
                        .build();
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    System.err.println("Bridge token revoke failed with status " + response.statusCode());
                }
            } catch (Exception exception) {
                System.err.println("Bridge token revoke failed: " + exception.getMessage());
            }
        });
    }

    private void deleteBridgeConfig() {
        try {
            Files.deleteIfExists(CONFIG_PATH);
        } catch (IOException ignored) {
            // Best effort: losing the in-memory token is enough for this process.
        }
    }

    private void updateAuthUi() {
        boolean authenticated = bridgeConfig != null && bridgeConfig.isComplete();
        if (authStatusValue != null) {
            authStatusValue.setText(authenticated ? bridgeConfig.twitchLogin() : "Not logged in");
        }
        if (loginButton != null) {
            loginButton.setVisible(!authenticated);
            loginButton.setText("Login with Twitch");
        }
        if (logoutButton != null) {
            logoutButton.setVisible(true);
            logoutButton.setEnabled(authenticated);
        }
        if (rememberLoginCheckbox != null) {
            rememberLoginCheckbox.setVisible(!authenticated);
        }
        if (logoutMenuItem != null) {
            logoutMenuItem.setEnabled(authenticated);
        }
        if (frame != null) {
            frame.revalidate();
            frame.repaint();
        }
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
        if (loginButton != null) {
            loginButton.setEnabled(enabled && applicationContext != null && applicationContext.isActive());
        }
        if (logoutButton != null) {
            logoutButton.setEnabled(enabled && bridgeConfig != null && bridgeConfig.isComplete());
        }
        if (rememberLoginCheckbox != null) {
            rememberLoginCheckbox.setEnabled(enabled);
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
        int size = 32;
        java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(size, size, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setRenderingHint(
                java.awt.RenderingHints.KEY_INTERPOLATION,
                java.awt.RenderingHints.VALUE_INTERPOLATION_BICUBIC
        );
        graphics.setRenderingHint(
                java.awt.RenderingHints.KEY_ANTIALIASING,
                java.awt.RenderingHints.VALUE_ANTIALIAS_ON
        );
        if (appIconImage != null) {
            graphics.drawImage(appIconImage, 0, 0, size, size, null);
        } else {
            graphics.setColor(new Color(25, 25, 25, 0));
            graphics.fillRect(0, 0, size, size);
            graphics.setColor(new Color(32, 29, 24));
            graphics.fillRoundRect(2, 2, 28, 28, 6, 6);
            graphics.setColor(new Color(247, 244, 238));
            graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
            graphics.drawString("MT", 7, 21);
        }

        int dotSize = 11;
        int dotX = size - dotSize - 2;
        int dotY = size - dotSize - 2;
        graphics.setColor(connected ? new Color(53, 190, 115) : new Color(216, 70, 70));
        graphics.fillOval(dotX, dotY, dotSize, dotSize);
        graphics.setColor(new Color(30, 30, 30));
        graphics.drawOval(dotX, dotY, dotSize, dotSize);
        graphics.dispose();
        return image;
    }

    private Image loadAppIcon() {
        try (InputStream stream = MtgoBridgeLauncher.class.getResourceAsStream(APP_ICON_RESOURCE)) {
            return stream == null ? null : ImageIO.read(stream);
        } catch (IOException exception) {
            return null;
        }
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

    private record BridgeConfig(
            String twitchLogin,
            String channelId,
            String relayFunctionUrl,
            String bridgePublishToken
    ) {
        private boolean isComplete() {
            return !isBlank(twitchLogin)
                    && !isBlank(channelId)
                    && !isBlank(relayFunctionUrl)
                    && !isBlank(bridgePublishToken);
        }
    }

    private record PkcePair(String codeVerifier, String codeChallenge) {
    }

    private record IssueBridgeTokenRequest(
            String code,
            String codeVerifier,
            String redirectUri
    ) {
    }

    private record IssuedBridgeToken(
            String channelId,
            String bridgeToken,
            String relayFunctionUrl
    ) {
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
