# OBS Auto-Launch Script

This optional OBS script starts the MTGO Twitch Bridge when streaming or recording begins.

## Setup

From the bridge window, click `Set up OBS auto-launch` to copy the script into OBS's scripts folder and pre-fill the bridge executable path.

Manual fallback:

1. In OBS, open `Tools > Scripts`.
2. Click `+` and select `obs/mtgo-twitch-bridge-launcher.lua`.
3. In the script properties, set `MTGO Twitch Bridge.exe` to your bridge executable.

The script launches the bridge with `--quiet-if-running`, so if the bridge is already open it exits quietly instead of showing a duplicate-instance dialog.

This is only a convenience on top of manual launch or Windows autostart. If no bridge path is configured, the script does nothing.
