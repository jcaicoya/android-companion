# CuarzoPolar — Android Companion

Mobile companion app for the **ataque-inicial** cybershow. Runs on an Android phone during a live theatrical performance.

Communicates with the Qt desktop app over WebSocket (port 8765, same Wi-Fi network).

## What it does

- Receives commands from the laptop operator: vibrate, play sound, show red screen
- Sends microphone transcripts (speech recognition) to the laptop for display
- Takes a selfie, sends it to the laptop for transformation, displays the result
- Shows connection status in real time

## First-time setup

1. Open this folder in **Android Studio** — it will prompt to download the Gradle wrapper, accept it
2. Create `local.properties` in the project root:
   ```
   sdk.dir=C\:\\Users\\caico\\AppData\\Local\\Android\\Sdk
   ```
3. Let Gradle sync complete
4. Run on a physical device (minSdk 26 / Android 8.0)

## Connecting to the laptop

Enter the laptop's IP address in the app (shown on the Qt setup screen as `ws://192.168.x.x:8765`). The IP is saved between sessions.

## Part of the CuarzoPolar show suite

| App | Role |
|---|---|
| `qr` | QR code interaction screen |
| `ataque-inicial` | Radar scan + device takeover (Qt, this app's counterpart) |
| `android-companion` | Mobile target (this app) |
