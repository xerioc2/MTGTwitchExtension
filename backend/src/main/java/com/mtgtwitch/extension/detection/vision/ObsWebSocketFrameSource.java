package com.mtgtwitch.extension.detection.vision;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

@Component
public class ObsWebSocketFrameSource implements FrameSource {

    private static final Logger log = LoggerFactory.getLogger(ObsWebSocketFrameSource.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(4);

    private final LocalVisionDetectorProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @Autowired
    public ObsWebSocketFrameSource(LocalVisionDetectorProperties properties, ObjectMapper objectMapper) {
        this(properties, objectMapper, HttpClient.newBuilder().connectTimeout(TIMEOUT).build());
    }

    ObsWebSocketFrameSource(
            LocalVisionDetectorProperties properties,
            ObjectMapper objectMapper,
            HttpClient httpClient
    ) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
    }

    @Override
    public Optional<CapturedFrame> capture() {
        if (properties.obsUrl().isBlank()) {
            return Optional.empty();
        }

        try (ObsSession session = ObsSession.connect(
                httpClient,
                objectMapper,
                URI.create(properties.obsUrl()),
                properties.obsPassword(),
                TIMEOUT
        )) {
            String sourceName = properties.obsSourceName();
            if (sourceName.isBlank()) {
                JsonNode scene = session.request("GetCurrentProgramScene", null);
                sourceName = firstText(scene.path("sceneName").asText(), scene.path("currentProgramSceneName").asText());
            }
            if (sourceName.isBlank()) {
                return Optional.empty();
            }

            ObjectNode requestData = objectMapper.createObjectNode();
            requestData.put("sourceName", sourceName);
            requestData.put("imageFormat", "jpg");
            requestData.put("imageWidth", properties.maxWidth());
            requestData.put("imageCompressionQuality", 75);
            JsonNode response = session.request("GetSourceScreenshot", requestData);
            byte[] imageBytes = decodeImageData(response.path("imageData").asText());
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(imageBytes));
            if (image == null) {
                return Optional.empty();
            }
            return Optional.of(new CapturedFrame(image, "OBS", Instant.now()));
        } catch (Exception exception) {
            log.debug("OBS frame capture skipped: {}", exception.getMessage());
            return Optional.empty();
        }
    }

    public static String createAuthentication(String password, String salt, String challenge) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String secret = Base64.getEncoder().encodeToString(
                    digest.digest((password + salt).getBytes(StandardCharsets.UTF_8))
            );
            return Base64.getEncoder().encodeToString(
                    digest.digest((secret + challenge).getBytes(StandardCharsets.UTF_8))
            );
        } catch (Exception exception) {
            throw new IllegalStateException("Could not create OBS authentication response.", exception);
        }
    }

    public static byte[] decodeImageData(String imageData) {
        if (imageData == null || imageData.isBlank()) {
            throw new IllegalArgumentException("OBS screenshot response did not contain image data.");
        }
        int separator = imageData.indexOf(',');
        String encoded = separator >= 0 ? imageData.substring(separator + 1) : imageData;
        return Base64.getDecoder().decode(encoded);
    }

    private static String firstText(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }

    private static final class ObsSession implements WebSocket.Listener, AutoCloseable {

        private final ObjectMapper objectMapper;
        private final Duration timeout;
        private final BlockingQueue<JsonNode> messages = new LinkedBlockingQueue<>();
        private final StringBuilder partialMessage = new StringBuilder();
        private volatile Throwable failure;
        private WebSocket webSocket;

        private ObsSession(ObjectMapper objectMapper, Duration timeout) {
            this.objectMapper = objectMapper;
            this.timeout = timeout;
        }

        static ObsSession connect(
                HttpClient httpClient,
                ObjectMapper objectMapper,
                URI uri,
                String password,
                Duration timeout
        ) throws Exception {
            ObsSession session = new ObsSession(objectMapper, timeout);
            session.webSocket = httpClient.newWebSocketBuilder()
                    .connectTimeout(timeout)
                    .buildAsync(uri, session)
                    .get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            JsonNode hello = session.awaitOperation(0);
            ObjectNode identifyData = objectMapper.createObjectNode();
            identifyData.put("rpcVersion", Math.min(1, hello.path("d").path("rpcVersion").asInt(1)));
            identifyData.put("eventSubscriptions", 0);
            JsonNode authentication = hello.path("d").path("authentication");
            if (!authentication.isMissingNode() && !authentication.isNull()) {
                identifyData.put("authentication", createAuthentication(
                        password == null ? "" : password,
                        authentication.path("salt").asText(),
                        authentication.path("challenge").asText()
                ));
            }
            session.send(1, identifyData);
            session.awaitOperation(2);
            return session;
        }

        JsonNode request(String requestType, ObjectNode requestData) throws Exception {
            String requestId = UUID.randomUUID().toString();
            ObjectNode data = objectMapper.createObjectNode();
            data.put("requestType", requestType);
            data.put("requestId", requestId);
            if (requestData != null && !requestData.isEmpty()) {
                data.set("requestData", requestData);
            }
            send(6, data);

            long deadline = System.nanoTime() + timeout.toNanos();
            while (System.nanoTime() < deadline) {
                JsonNode message = nextMessage(deadline);
                JsonNode response = message.path("d");
                if (message.path("op").asInt(-1) != 7 || !requestId.equals(response.path("requestId").asText())) {
                    continue;
                }
                if (!response.path("requestStatus").path("result").asBoolean(false)) {
                    throw new IllegalStateException("OBS request failed: "
                            + response.path("requestStatus").path("comment").asText(requestType));
                }
                return response.path("responseData");
            }
            throw new IllegalStateException("Timed out waiting for OBS request " + requestType + ".");
        }

        private void send(int operation, ObjectNode data) throws Exception {
            ObjectNode message = objectMapper.createObjectNode();
            message.put("op", operation);
            message.set("d", data);
            webSocket.sendText(objectMapper.writeValueAsString(message), true)
                    .get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        }

        private JsonNode awaitOperation(int operation) throws Exception {
            long deadline = System.nanoTime() + timeout.toNanos();
            while (System.nanoTime() < deadline) {
                JsonNode message = nextMessage(deadline);
                if (message.path("op").asInt(-1) == operation) {
                    return message;
                }
            }
            throw new IllegalStateException("Timed out waiting for OBS operation " + operation + ".");
        }

        private JsonNode nextMessage(long deadline) throws Exception {
            if (failure != null) {
                throw new IllegalStateException("OBS WebSocket failed.", failure);
            }
            long remainingMillis = Math.max(1, TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime()));
            JsonNode message = messages.poll(remainingMillis, TimeUnit.MILLISECONDS);
            if (message == null) {
                throw new IllegalStateException("Timed out waiting for OBS WebSocket response.");
            }
            return message;
        }

        @Override
        public void onOpen(WebSocket webSocket) {
            webSocket.request(1);
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            partialMessage.append(data);
            if (last) {
                try {
                    messages.offer(objectMapper.readTree(partialMessage.toString()));
                } catch (Exception exception) {
                    failure = exception;
                } finally {
                    partialMessage.setLength(0);
                }
            }
            webSocket.request(1);
            return null;
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            failure = error;
        }

        @Override
        public void close() {
            if (webSocket != null) {
                webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "done");
            }
        }
    }
}
