package com.mtgtwitch.extension.detection.vision;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class ObsWebSocketFrameSourceTests {

    @Test
    void createsObsWebSocketFiveAuthenticationResponse() {
        String authentication = ObsWebSocketFrameSource.createAuthentication(
                "supersecretpassword",
                "lM1GncleQOaCu9lT1yeUZhFYnqhsLLP1G5lAGo3ixaI=",
                "+IxH4CnCiqpX1rM9scsNynZzbOe4KhDeYcTNS3PDaeY="
        );

        assertThat(authentication).isEqualTo("1Ct943GAT+6YQUUX47Ia/ncufilbe6+oD6lY+5kaCu4=");
    }

    @Test
    void decodesObsScreenshotDataUri() {
        String encoded = Base64.getEncoder().encodeToString("frame".getBytes(StandardCharsets.UTF_8));

        byte[] decoded = ObsWebSocketFrameSource.decodeImageData("data:image/jpeg;base64," + encoded);

        assertThat(new String(decoded, StandardCharsets.UTF_8)).isEqualTo("frame");
    }
}
