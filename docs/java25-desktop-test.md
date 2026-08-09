# MTGO Twitch Bridge Java 25 Test Kit

This is an experimental Bridge 0.0.14 candidate. It uses a bundled Java 25.0.4
runtime and Spring Boot 3.5.16 while keeping the application's source and
bytecode compatibility at Java 21.

The GameState payload shape is unchanged. This kit includes a rebuilt Twitch
frontend with the relay lifecycle and detection-event hardening, so upload it
only to a separate Twitch Local/Hosted Test version while validating the full
candidate. The bridge can still be tested by itself against the currently
released frontend.

## Recommended Test Order

1. Shut down every currently running MTGO Twitch Bridge instance from its tray
   icon.
2. Run `Launchers/Run Java25 Test - Isolated.cmd` first. This uses the kit's
   local `test-appdata` folder and clears live relay environment variables.
3. Verify the window, tray icon, log discovery, foreground-game tracking, and
   clean shutdown. The isolated run will not have your saved Twitch login.
4. Run `Launchers/Run Java25 Test - Current Settings.cmd` for the real stream
   test. This uses your existing bridge configuration and relay credentials.
5. Test game switching between MTGO accounts, sideboarding between games,
   Reconnect, and viewer updates on Twitch.
6. Use the installer only after the portable build passes. The installer and
   portable build contain the same application code and Java runtime.

Do not run the current release and this candidate at the same time. The bridge's
single-instance guard should stop the second copy, but a clean one-at-a-time
test produces clearer logs.

If Windows autostart is enabled in your existing configuration, a normal test
launch may self-heal the registry entry to the candidate executable path. After
testing, launch your preferred installed release once to point autostart back to
that executable.

## Files

- `MTGO Twitch Bridge-0.0.14.exe`: Java 25 Windows installer candidate.
- `MTGO-Twitch-Bridge-0.0.14-portable.zip`: uploadable portable candidate.
- `magiccontent-upload.zip`: production Twitch frontend upload, with files at
  the archive root and the Twitch Extension Helper loaded first.
- `magiccontent-v2-screen-hover-test.zip`: optional V2-only frontend with the
  experimental detection hitbox layer enabled.
- `Portable/MTGO Twitch Bridge/`: extracted portable build for immediate use.
- `Launchers/`: normal and isolated test launchers.
- `obs/`: optional OBS auto-launch script and instructions.
- `java25-upgrade-plan.md`: full compatibility and manual-test plan.
- `SHA256SUMS.txt`: integrity hashes for both frontends, the installer, and the
  portable zip.

## Release Gate

Do not publish this as the latest bridge until the tray, OAuth, JNA focus
tracking, multiple-account log switching, sideboard deck refresh, autostart,
OBS launch, installer upgrade/uninstall, and live relay checks pass.
