# Networking Architecture & Alternatives

This document captures the exploration of different wireless networking strategies for connecting the `ataque-inicial` Qt desktop application (laptop) with the `android-companion` mobile application (actor's phone) during a live theatrical performance.

## Current Architecture: Wi-Fi + WebSockets

The currently implemented architecture relies on a local Wi-Fi network.
*   **Protocol:** WebSockets (Port 8765)
*   **Transport:** JSON for commands/transcripts, Binary frames for JPEG photos.
*   **Setup:** Both devices connect to the same local area network (LAN). The phone connects to the laptop's IP address.

### Challenge
Manually typing the laptop's IP address into the phone before every show is prone to error and friction.

---

## Explored Alternatives for Device Connectivity

We explored several wireless methods to connect the Windows laptop directly to the Android phone.

### 1. The "Hotspot" Method (Software Access Point) - *Highly Recommended*

Instead of bringing a physical Wi-Fi router to the theater, one device acts as the router.
*   **How it works:** The Windows laptop turns on its built-in "Mobile Hotspot" (e.g., "CuarzoPolar-Stage"). The Android phone connects to this Wi-Fi network.
*   **Pros:**
    *   **Zero Code Changes:** The existing WebSocket code works perfectly without modifications.
    *   **No Extra Hardware:** Eliminates the need for a physical stage router.
    *   **High Bandwidth & Range:** Uses standard Wi-Fi, easily handling instant JPEG photo transfers and low-latency cues.
    *   **Predictable IP:** The laptop hosting the hotspot usually assigns itself a fixed IP (like `192.168.137.1` on Windows). The Android app can hardcode or default to this IP, eliminating manual entry.
*   **Cons:** Relies on the laptop's Wi-Fi chip being able to broadcast effectively to the stage.

### 2. Wi-Fi with UDP Auto-Discovery (The "MAC Filter" Alternative)

If using a standard router or hotspot, we can automate the connection process to avoid typing IPs.
*   **How it works:**
    *   **Discovery:** The Qt app broadcasts a small UDP packet (e.g., on port 8766) every 2 seconds containing its IP and WebSocket port. The Android app listens on this port and automatically connects when it hears the beacon.
    *   **Authentication (Replacing MAC Filtering):** Android restricts reading the real hardware MAC address for privacy. Instead, the Android app generates a persistent `UUID` on first install. It sends this ID upon connection. The Qt app maintains a whitelist of allowed `deviceId`s and drops unknown connections.
*   **Pros:** Seamless setup; zero configuration needed by the operator.
*   **Cons:** Requires writing UDP broadcast/listener code on both C++ and Kotlin sides.

### 3. Bluetooth Classic (RFCOMM/SPP) - *Discarded*

Connecting the devices via standard Bluetooth pairing.
*   **Pros:** No IP addresses to manage; completely independent of Wi-Fi infrastructure.
*   **Cons (Fatal for Live Shows):**
    *   **Audience Interference:** The 2.4GHz Bluetooth spectrum becomes extremely noisy with 100+ smartphones in the audience, risking high latency or dropped connections exactly when a stage cue (like a vibration) is needed.
    *   **Bandwidth Limits:** Maxes out at 2-3 Mbps. Transferring compressed JPEG photos takes noticeably longer than Wi-Fi. It cannot support future Phase 5 video streaming.
    *   **Pairing Friction:** Requires manual OS-level pairing before the app can be used.
    *   **Architecture rewrite:** Would require discarding WebSockets and implementing `QBluetoothServer` (Qt) and Bluetooth sockets (Android).

### 4. Wi-Fi Direct (Wi-Fi P2P) - *Discarded*

Allows devices to connect directly via Wi-Fi without a router/hotspot.
*   **Pros:** High speed and good range.
*   **Cons:** The APIs are notoriously difficult to bridge seamlessly between Windows C++ and Android Kotlin. The complex connection-negotiation code required makes this unviable compared to the simplicity of the Hotspot method, which achieves the exact same physical result.

### 5. Esoteric Methods - *Discarded*

*   **Audio Data Transmission (Ultrasound):** Sending data via high-frequency audio picked up by the mic. *Reason for discard:* Bandwidth is only a few bytes per second; impossible for photos.
*   **NFC (Near Field Communication):** *Reason for discard:* Range is only a few centimeters; impossible for remote stage control.

---

## Conclusion & Recommendation

For a live theatrical performance where timing, reliability, and bandwidth (for photos) are critical:

1.  **Transport:** Stick with the **Wi-Fi + WebSockets** architecture. It is the industry standard for high-bandwidth, low-latency local control.
2.  **Infrastructure:** Use the **Windows Mobile Hotspot** method to avoid bringing a physical router while maintaining a predictable IP address.
3.  **Future Enhancement:** Implement **UDP Auto-Discovery** combined with a UUID whitelist if manual IP entry remains a friction point.
