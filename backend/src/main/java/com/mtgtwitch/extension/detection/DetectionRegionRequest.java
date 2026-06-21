package com.mtgtwitch.extension.detection;

import java.util.List;

public record DetectionRegionRequest(
        String channelId,
        List<DetectionRegion> regions
) {
}
