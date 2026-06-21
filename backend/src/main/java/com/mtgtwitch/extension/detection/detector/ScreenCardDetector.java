package com.mtgtwitch.extension.detection.detector;

import com.mtgtwitch.extension.detection.DetectionRegion;
import com.mtgtwitch.extension.gamestate.GameState;

import java.util.List;

public interface ScreenCardDetector {

    List<DetectionRegion> detect(GameState currentGameState, DetectorContext detectorContext);
}
