# :platform:midi

**Tipo:** Android library · **Carpeta:** `platform/midi/` · **Depende de:** —

## Responsabilidad

Entrada MIDI de Android (USB/Bluetooth): listar dispositivos, conectar y decodificar el flujo de bytes en eventos (`MidiMessageParser`, máquina de estados tolerante a mensajes fragmentados). Expone el contrato `MidiInput`.

## API principal

`MidiInput`, `MidiInputManager`, `MidiMessageParser`, `MidiNoteEvent`, `MidiDeviceSummary`

## Qué NO debe contener

Decidir qué muestra suena: eso lo hace la capa de sesión de `:app` con `SampleZoneResolver`.

## Contenido

5 archivos de producción · 37 pruebas en 1 clases.

| Paquete (`com.jvk.dwpcreator.…`) | Archivos |
|---|---:|
| `midi` | 5 |

## Pruebas

```bash
gradle :platform:midi:testDebugUnitTest
```

Reglas del proyecto (se comprueban en CI con `python3 tools/check_architecture.py`): un tipo por archivo,
dependencias solo hacia abajo y sin ciclos. Ver `Doc/72_MODULAR_ARCHITECTURE_REFACTOR.md`.
