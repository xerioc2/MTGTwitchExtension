package com.mtgtwitch.extension.detection.vision;

import java.util.Optional;

public interface FrameSource {

    Optional<CapturedFrame> capture();
}
