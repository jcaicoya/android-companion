# Permission Android

Aplicación móvil compañera de `permission_qt` dentro del show CuarzoPolar. Se ejecuta en el teléfono o tablet del show y actúa como objetivo móvil controlado por la app Qt del portátil.

## Qué es

La app Android es un companion controlado. Su UI visible es mínima y durante el show el comportamiento lo gobierna `permission_qt`.

## Función dentro del show

- recibe órdenes desde la app Qt
- actúa como objetivo móvil del módulo
- muestra estados visuales o ejecuta acciones del dispositivo
- devuelve información al módulo Qt cuando corresponde

## Estado actual

La app es ya un companion casi silencioso:

- UI mínima con indicador de conexión y estado
- conexión automática al portátil por WebSocket
- fallback manual cuando no hay conexión automática
- servicio foreground persistente para mantener la conexión

## Capacidades implementadas

- auto-discovery del portátil Qt por beacon UDP
- fallback manual por IP
- servicio foreground que mantiene WebSocket
- recepción de comandos desde Qt:
  - `VIBRATE`
  - `SOUND`
  - `BLOCK`
  - `STREAM`
  - `MIC`
  - `PHOTO`
- envío de transcripciones de voz
- captura de foto frontal y retorno al flujo Qt
- streaming de vídeo de cámara a Qt
- modo de pantalla roja durante `BLOCK`
- comportamiento fail-open al perder conexión
- al cerrar la app, desconexión limpia y parada del servicio

## Arquitectura y comunicación

- Aplicación Android nativa.
- Cliente WebSocket hacia `permission_qt`.
- Conexión preferente por ADB reverse a `localhost:8765`.
- Fallback por Wi-Fi directo y auto-descubrimiento UDP.

Endpoints previstos:

```text
ws://localhost:8765
ws://<laptop-ip>:8765
```

## Tecnología

| Capa | Tecnología |
|---|---|
| Plataforma | Android |
| Comunicación | WebSocket + UDP discovery |
| Ejecución persistente | Foreground service |
| Captura | cámara y micrófono del dispositivo |

## Reglas funcionales del show

- Qt es la fuente de verdad durante la función.
- Android no debe exponer controles de ensayo al actor.
- Si Qt se desconecta, Android vuelve a modo normal.
- Si Android sale, Qt debe pasar a `SIN ENLACE`.
- La pantalla roja solo debe permanecer activa mientras Qt siga conectado y la ordene.

## Suite CuarzoPolar

| App | Rol |
|---|---|
| `qr` | pantalla de interacción QR |
| `permission_qt` | consola de radar y control móvil |
| `permission_android` | objetivo móvil controlado |
