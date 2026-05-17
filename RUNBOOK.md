# Permission Android Runbook

## Deploy

### Build local

1. Abrir este directorio en Android Studio.
2. Comprobar `local.properties` con la ruta correcta al Android SDK.
3. El proyecto fija Gradle al JBR incluido en Android Studio mediante `gradle.properties`.
4. Compilar y ejecutar en dispositivo físico Android.

No se recomienda usar emulador para validar cámara, micrófono, lock screen o foreground service.

## Arranque y conexión

### Flujo preferente con orchestrator

1. Conectar el dispositivo Android por USB o WiFi ADB.
2. En Orchestrator -> `CONFIGURAR -> ADB`, pulsar `Detectar`.
3. En `ENSAYO -> Apps Android`, lanzar `Companion`.
4. El orchestrator ejecuta `adb reverse tcp:8765 tcp:8765` y lanza la app.
5. Arrancar `permission_qt`.
6. Android conecta a `localhost:8765`.

### Flujo alternativo sin orchestrator

1. Arrancar `permission_qt` en el portátil.
2. Poner portátil y Android en la misma red Wi-Fi.
3. Android intenta primero `localhost:8765`.
4. Si falla, escucha el beacon UDP y conecta automáticamente.
5. Si también falla, introducir IP del portátil manualmente desde la UI desconectada.

## Manejo de la aplicación

- En desconectado, tocar el estado para abrir la conexión manual por IP.
- En conectado, el control lo lleva la app Qt.
- El operador/actor no debería tener controles de ensayo visibles.

## Kiosk / Device Owner

Para un comportamiento de kiosk real durante `BLOCK`, el dispositivo debe provisionarse como device owner.

Ejemplo típico en dispositivo recién reseteado:

```powershell
adb install app\build\outputs\apk\debug\app-debug.apk
adb shell dpm set-device-owner com.cuarzopolar.permission/.PermissionDeviceAdminReceiver
```

Si Android lo rechaza por estar ya provisionado o con cuentas, hay que resetear de fábrica y repetir antes en el proceso de setup.

## Consideraciones operativas

- Si se cae el WebSocket, Android debe parar micrófono, streaming, pantalla roja y volver a modo normal.
- Si la app se cierra o se swipea, debe desconectar WebSocket y parar el servicio foreground.
- Probar siempre comportamiento real en dispositivo físico de show.
