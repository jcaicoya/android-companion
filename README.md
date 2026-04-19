# CuarzoPolar - Android Companion

Mobile companion app for the `ataque-inicial` cybershow. It runs on the actor/show tablet or phone and acts as the controlled mobile target for the Qt laptop app.

The app communicates with the Qt desktop app over WebSocket on port `8765`, using the same local Wi-Fi network or Windows Mobile Hotspot.

## Current Status

The Android app is now a mostly silent companion. The visible UI is minimal: a large connection dot and status label. When disconnected, tapping the status opens a bottom sheet for manual IP connection. When connected, the laptop controls the show actions.

Implemented:

- Auto-discovery of the Qt laptop by UDP beacon, with manual IP fallback.
- Persistent foreground service that owns the WebSocket connection while the app is running.
- Commands from Qt:
  - `VIBRATE`
  - `SOUND`
  - `BLOCK` / red screen
  - `STREAM`
  - `MIC`
  - `PHOTO`
- Speech recognition transcripts sent to Qt.
- Front-camera still photo capture sent to Qt, transformed there, and returned to Android.
- Camera video streaming to Qt.
- Red screen mode:
  - full-screen red activity
  - shows over lock screen where Android allows it
  - ignores Back / normal finish while active
  - supports lock-task/kiosk mode if the app is provisioned as device owner
- Fail-open disconnect behavior:
  - if the WebSocket connection drops, Android stops mic, stops camera streaming, hides red screen, and returns to normal mode.
- User-exit behavior:
  - if the Android app is closed/swiped away, it disconnects the WebSocket and stops the foreground service so Qt switches to `SIN ENLACE`.

## Build Notes

1. Open this folder in Android Studio.
2. Ensure `local.properties` exists in the project root:

   ```properties
   sdk.dir=C\:\\Users\\caico\\AppData\\Local\\Android\\Sdk
   ```

3. The project pins Gradle to Android Studio's bundled JBR in `gradle.properties`:

   ```properties
   org.gradle.java.home=C:/Program Files/Android/Android Studio/jbr
   ```

   This avoids running Android Gradle Plugin under the system Java 8 runtime.

4. Build/run on a physical Android device. Emulator use is not recommended because camera, mic, lock-screen, and foreground-service behavior differ from show hardware.

## Connecting To The Laptop

Preferred show flow:

1. Start the Qt `ataque-inicial` app on the laptop.
2. Put laptop and Android device on the same Wi-Fi network, ideally the laptop's Windows Mobile Hotspot.
3. Android listens for the Qt UDP beacon and connects automatically.
4. If auto-discovery fails, tap the Android status dot while disconnected and enter the laptop IP manually.

Qt WebSocket endpoint:

```text
ws://<laptop-ip>:8765
```

## Kiosk / Device Owner Option

Normal Android apps cannot make it literally impossible to leave the app. For true kiosk behavior during `BLOCK`, provision the tablet/phone as a dedicated device and set this app as device owner.

Typical setup on a freshly reset Android device:

```powershell
adb install app\build\outputs\apk\debug\app-debug.apk
adb shell dpm set-device-owner com.cuarzopolar.companion/.CompanionDeviceAdminReceiver
```

If Android refuses because the device is already provisioned or has accounts, factory reset and run the command earlier in setup.

## Show Behavior Rules

- Qt is the source of truth during the show.
- Android should not expose rehearsal controls to the actor/operator.
- If Qt disconnects, Android returns to normal mode.
- If Android exits, Qt must show `SIN ENLACE`.
- Red screen should remain active only while Qt is connected and commanding it.

## Next Steps

- Rehearse kiosk/device-owner setup on the actual show tablet.
- Decide whether the production device should run in normal app mode or lock-task mode.
- Add a clear operator checklist for pre-show connection validation.
- Test connection loss intentionally during rehearsal:
  - close Qt
  - close Android
  - disable Wi-Fi
  - lock/unlock Android during red screen
- Tune video stream resolution/FPS if laptop rendering or Wi-Fi latency becomes unstable.

## Part Of The CuarzoPolar Show Suite

| App | Role |
|---|---|
| `qr` | QR code interaction screen |
| `ataque-inicial` | Radar scan + mobile control console |
| `android-companion` | Controlled mobile target |
