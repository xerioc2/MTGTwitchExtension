# Experimental Local Screen Detector

The bridge includes an optional local detector that can capture the OBS program scene or a calibrated desktop area, find card-shaped rectangles, and reconcile them against cards already known from MTGO `GameState`.

It is disabled by default and does not change normal zone tracking. It is an experimental progressive-enhancement path; the hosted Twitch-HLS vision service remains the bridge-optional architecture.

## Enable it in the desktop bridge

1. Open **Detector settings**.
2. Check **Enable experimental local screen detection**.
3. Choose **OBS** or **SCREENSHOT**.
4. Save and restart the bridge.

The bridge scans every five seconds by default. Detection regions still pass through the existing normalization, TTL, and relay path.

## OBS mode

OBS Studio 28 or newer includes obs-websocket 5.x. In OBS, open **Tools > WebSocket Server Settings**, enable the server, and copy its password into the bridge detector settings. The default URL is `ws://127.0.0.1:4455`.

Leave the scene/source field blank to capture the current program scene. Set it only when a specific OBS scene or source should always be analyzed. The adapter requests an in-memory JPEG from OBS and does not write capture files.

## Screenshot mode

Screenshot mode captures a normalized area of the Windows virtual desktop. Click **Select screen area...**, then drag around the MTGO content that corresponds to the broadcast video. The resulting normalized crop works across desktop resolutions without storing fixed pixel dimensions.

Because local screen capture is ahead of Twitch's delayed viewer video, screenshot mode is intended for calibration and detector evaluation. OBS program-scene capture has correct broadcast-space geometry but is still temporally ahead of viewers.

## Identity and confidence

1. OpenCV finds portrait card rectangles.
2. Perceptual template matching compares each crop with Scryfall art for cards in the current `GameState`.
3. Optional Tesseract OCR reads the title bar and fuzzy-matches only against that same known-card list.
4. The confidence scorer rewards agreement, rejects ambiguous conflicts, and publishes only results above the configured threshold.

The detector never invents a card identity. If neither template matching nor OCR can reconcile a rectangle to a known card, the rectangle is dropped.

## Optional OCR

OCR is disabled by default. Install Tesseract and either put `tesseract.exe` on `PATH` or select its full executable path in detector settings. A missing executable does not stop the bridge; OCR is skipped and template matching remains available.

## Environment configuration

Desktop settings are saved in the bridge's existing `%APPDATA%\MTGO Twitch Bridge\config.properties`. For command-line development, all equivalent `SCREEN_DETECTOR_*` variables are listed in `.env.example`.

To return to normal bridge behavior, clear **Enable experimental local screen detection** and restart.
