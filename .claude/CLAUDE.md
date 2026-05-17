# CLAUDE.md - Permission Android

Instrucciones de trabajo específicas para este subproyecto.

## Lectura obligatoria al empezar

Antes de trabajar aquí, lee y aplica también:

- `README.md`
- `RUNBOOK.md`
- `NEXT_STEPS.md`

## Qué contiene cada archivo

- `README.md`: qué es la app, rol en show, capacidades, arquitectura y reglas funcionales.
- `RUNBOOK.md`: build/deploy, arranque, conexión, operación y modo kiosk.
- `NEXT_STEPS.md`: pendientes actuales.
- `.claude/CLAUDE.md`: reglas de trabajo específicas de este directorio.

No dupliques información entre estos archivos. Cada dato debe vivir en un único sitio.

## Forma de trabajar en este directorio

- El usuario se encarga de compilar, probar, hacer commits y hacer push.
- Si cambias comportamiento de conexión, operación, capacidades del dispositivo o backlog, actualiza el archivo correspondiente.
- Tras cada commit, `README.md`, `RUNBOOK.md` y `NEXT_STEPS.md` deben seguir reflejando el estado real del proyecto.
- Las validaciones importantes deben pensarse sobre hardware Android real, no sobre emulador.

## Alcance de este archivo

Este archivo no debe repetir documentación general ni instrucciones operativas de uso de la app; eso pertenece a `README.md` o `RUNBOOK.md`.
