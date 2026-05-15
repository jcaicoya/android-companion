# Networking - Permission Android

This document tracks the current network behavior between the Android companion app and the Qt `permission_qt` laptop app.

Everything here is for theatrical show control. No real device compromise is performed.

## Current Architecture

- Qt laptop app (`permission_qt`): WebSocket server on port `8765`.
- Android companion: WebSocket client.
- **Primary connection path:** ADB reverse tunnel (`adb reverse tcp:8765 tcp:8765`) set up by the
  Orchestrator before launching the Android app. Android connects to `localhost:8765`.
- **Fallback path (no ADB/Orchestrator):** Android tries `localhost:8765` for 3 retries (~7 s),
  then falls back to UDP beacon discovery, then manual IP entry.
- Text protocol: compact JSON over WebSocket text frames.
- Binary protocol: JPEG data over WebSocket binary frames.
- Heartbeat: Qt sends `ping`; Android replies `pong`.

## Recommended Show Network

Use the laptop as the stage network:

1. Enable Windows Mobile Hotspot on the laptop.
2. Connect the Android show device to that hotspot.
3. Launch Qt.
4. Launch Android companion.
5. Wait for `ENLACE ACTIVO` / green dot on both sides.

This avoids venue Wi-Fi variability and usually gives the laptop a predictable address.

## Connection Lifecycle

### Startup

1. Android starts `PermissionService` as a foreground service.
2. Android tries `localhost:8765` immediately (ADB reverse tunnel path).
3. If 3 retries fail (~7 s), Android starts `UdpDiscovery` and listens for Qt's beacon.
4. Manual fallback: tap the Android status dot to enter the laptop IP.
5. On connection, Android sends:

```json
{"type":"status","deviceName":"<android model>"}
```

### Healthy Connection

Qt sends:

```json
{"type":"ping"}
```

Android replies:

```json
{"type":"pong"}
```

Qt treats missed heartbeats as a disconnect and updates the UI to `SIN ENLACE`.

### Connection Loss

When Android detects `DISCONNECTED`, it returns to normal mode:

- stop speech recognition
- clear microphone foreground-service state
- stop video streaming
- hide red screen
- keep discovery/reconnect running unless the app is being closed

### Android App Closed

If the user closes/swipes away the Android app, the service now performs an operator shutdown:

- clear saved IP
- stop discovery
- stop mic/camera/red screen
- close the WebSocket
- stop the service

This is important because Qt should not remain on `ENLACE ACTIVO` after the Android app is closed.

## Message Protocol

### Qt To Android

| Action | Message |
|---|---|
| Vibrate | `{"type":"command","action":"vibrate","targetId":"..."}` |
| Sound | `{"type":"command","action":"playSound","targetId":"..."}` |
| Show red screen / block | `{"type":"command","action":"showRedScreen","targetId":"..."}` |
| Hide red screen / unblock | `{"type":"command","action":"hideRedScreen","targetId":"..."}` |
| Start microphone | `{"type":"command","action":"startMic","targetId":"..."}` |
| Stop microphone | `{"type":"command","action":"stopMic","targetId":"..."}` |
| Take photo | `{"type":"command","action":"takePhoto","targetId":"..."}` |
| Start video stream | `{"type":"command","action":"startStream"}` |
| Stop video stream | `{"type":"command","action":"stopStream"}` |
| Heartbeat | `{"type":"ping"}` |

### Android To Qt

| Purpose | Message |
|---|---|
| Heartbeat reply | `{"type":"pong"}` |
| Connection status | `{"type":"status","deviceName":"..."}` |
| Speech transcript | `{"type":"transcript","text":"..."}` |
| Photo marker | `{"type":"photo_ready"}` followed by binary JPEG |
| Stream started | `{"type":"stream_start"}` |
| Stream stopped | `{"type":"stream_stop"}` |
| Video frame | binary JPEG while streaming |

### Qt To Android Binary

After Android sends a still photo, Qt transforms it into a thermal-style image and returns the transformed JPEG as a binary WebSocket frame.

## Ports

| Purpose | Port |
|---|---|
| WebSocket control/data | `8765` |
| UDP auto-discovery beacon | see `UdpBeacon` / `UdpDiscovery` implementation |

## Operational Rules

- Android should only activate mic/camera/red screen while connected to Qt.
- On any disconnect, Android returns to normal mode.
- Qt action buttons should be disabled when no Android client is connected.
- Qt STREAM uses optimistic pending UI:
  - button flips immediately
  - Android confirmation keeps it active
  - timeout reverts it

## Troubleshooting

### Qt says `SIN ENLACE`

- Confirm Android and laptop are on the same Wi-Fi/hotspot.
- Confirm Qt is running before Android tries auto-discovery.
- Try manual IP entry from Android's disconnected status sheet.
- Check Windows firewall for port `8765`.

### Qt stays `ENLACE ACTIVO` after closing Android

This should now be fixed for normal app close/swipe-away paths. If it happens again:

- confirm the updated APK is installed
- check whether Android is keeping the process alive due to OEM battery/service policy
- close the task from Recents and wait for heartbeat timeout

### Mic Does Not Send Transcripts

- Confirm Android has `RECORD_AUDIO` permission.
- Confirm foreground-service microphone permission exists on Android 14+.
- Confirm the app is connected before pressing `MIC`.

### Stream Button Times Out

- Confirm Android has `CAMERA` permission.
- Confirm camera is not already locked by another app.
- Confirm the device is not in a privacy mode blocking camera use.

## Next Steps

- Verify UDP discovery port and document it explicitly in both code and docs.
- Add an operator pre-show network checklist.
- Test on the exact show tablet and laptop using Windows Mobile Hotspot.
- Decide whether kiosk/device-owner mode will be used for the final show device.
