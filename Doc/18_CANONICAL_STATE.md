# 18 — ESTADO CANÓNICO ACTUAL DEL PROYECTO

Este documento es la única fuente de verdad agregada del proyecto en este momento. Cualquier otro documento de `Doc/` que lo contradiga debe explicar por qué está desactualizado respecto a este.

**Fecha de este estado:** tras "Core Stabilization — Final Verification" (`Doc/22_CORE_STABILIZATION_FINAL_VERIFICATION.md`). Supera y reemplaza la versión anterior (que reflejaba el estado tras `Doc/21_CORE_STABILIZATION_PASS3.md`). Sin cambios de comportamiento del motor DWP/WAV/MIDI en esta pasada; el alcance fue corrección de 2 warnings de compilación (§A), mejora del workflow de CI para reportar resultados reales de tests, y corrección de documentación desactualizada (incluyendo un caso donde un documento describía un bug ya corregido como si siguiera activo — ver `Doc/22_CORE_STABILIZATION_FINAL_VERIFICATION.md` §11).

## A. Implementado

- **DWP parsing**: modelo físico tag/length/reserved/payload, LE, sin offsets absolutos. Round-trip binario exacto verificado (SHA-256 del fixture real confirmado sin cambios: `d7699c3c...41925`).
- **Strict parsing**: `DwpTokenizer.tokenizeStrict()` con razones de parada estructuradas (`StopReason`); usado en toda operación que reserializa.
- **Preservation**: bloques desconocidos preservados verbatim, verificado explícitamente tras una modificación conocida (rename) sobre las 48 muestras.
- **Sample metadata**: `0x01f4` (key range, orden root/low/high correcto), `0x01f5`/`0x01f6` (nombre/ruta), `0x01f7` (formato de audio) con validación de unicidad/tamaño exacto en mutación y **lectura tolerante** en `listSamples()` (nunca lanza excepción ante un `0x01f7` malformado — "lectura tolerante, mutación estricta").
- **DWP versioning**: `DwpVersionProfile` — perfil confirmado único: v0x26, preámbulo 90 bytes. Versión desconocida rechazada explícitamente.
- **WAV parsing**: PCM 8/16/24/32-bit, float32, WAVE_FORMAT_EXTENSIBLE (`cbSize`, GUID completo, `validBitsPerSample` en rango). Coherencia RIFF-declarado ↔ archivo real validada, **incluido el byte de padding de chunks de tamaño impar**. `blockAlign`/`byteRate` validados matemáticamente contra `channels`/`bitsPerSample`/`sampleRate`.
- **PCM conversion**: a 16-bit para preview únicamente; nunca se usa en el pipeline de exportación.
- **ZIP project I/O**: carga/exportación con protección contra múltiples `.dwp`, nombres de `.wav` ambiguos, path traversal, nombres duplicados, y límites de recursos (`MAX_ENTRIES` — **ahora cuenta directorios también** —, `MAX_SINGLE_ENTRY_BYTES`, `MAX_TOTAL_UNCOMPRESSED_BYTES`) verificados durante la lectura.
- **MIDI subset**: `MidiMessageParser`, ahora una **máquina de estados incremental real** — Note On/Off (incluida velocity 0), running status correctamente acotado a mensajes de canal (`0x80`-`0xEF`), System Realtime/Common manejados por separado, y **mensajes fragmentados entre múltiples buffers se ensamblan correctamente** sin perder datos. Kotlin/JVM puro, separado de Android (`MidiInputManager`). `MidiInputManager.allDevices()` (Final Verification) reemplaza el uso directo de `MidiManager.getDevices()` (deprecada en API 30) por un helper con gate de versión.
- **Preview**: reproducción nativa vía `AudioTrack`, con validación de `channelCount` y límite de voces concurrentes.

## B. No implementado (fuera de alcance, deliberadamente)

- DWP Monolithic — **NOT IMPLEMENTED**
- Audio embebido — **NOT IMPLEMENTED**
- FLAC (encoder/decoder) — **NOT IMPLEMENTED**
- `0x0205` / `0x0206` — sin evidencia, sin código (verificado por búsqueda exhaustiva, 0 coincidencias)
- Generación de un `.dwp` completo desde cero (siempre se parte de una plantilla real)
- Rename/delete de sample individual (UI oculta desde Pass 1, no implementada)
- Gradle Wrapper (documentado como limitación del entorno, no del proyecto — ver §F)

## C. UNKNOWN (campos DWP sin semántica demostrada)

Sin cambios respecto a la pasada anterior: `0x01f4` bytes 3-24, `0x01f7` offsets +04/+20/+24/+28/+32 (y ahora también +08/+0C/+10/+14/+18/+1C/+28/+2C nombrados explícitamente en el prompt de Pass 3 — ninguno tocado ni interpretado), `0x01f8`-`0x0203`, semántica de `0x0204`, contenido interno del preámbulo de 90 bytes, `0x0205`/`0x0206`.

## D. Código actual

31 archivos Kotlin `main` (sin nuevos en esta pasada — todos los cambios de Pass 3 fueron ediciones de archivos existentes), 5 en `test`. Ningún componente cambió de capa ni de responsabilidad; `MidiMessageParser` pasó de "parser sin memoria entre llamadas" a "máquina de estados incremental", un cambio de comportamiento interno, no de arquitectura.

## E. Tests actuales

**125 métodos `@Test`** en 5 archivos (antes de esta pasada: 97). `DwpEngineTest.kt` 48, `WavDecoderTest.kt` 28, `MidiMessageParserTest.kt` 29, `ZipProjectIoTest.kt` 14, `PcmConverterTest.kt` 6.

**TEST WRITTEN, NO TEST EXECUTED.** Ninguno de los 125 tests se ha ejecutado en ningún entorno con Android SDK/Gradle real. Toda verificación hasta ahora es por lectura completa y trazado manual paso a paso contra la implementación exacta.

## F. Documentación actual

23 archivos en `Doc/` (00 a 22). Documento nuevo de esta pasada:
`22_CORE_STABILIZATION_FINAL_VERIFICATION.md`. Corregidos en esta pasada por
describir estado desactualizado o incorrecto respecto al código real:
`09_TEST_AUDIT.md` (nota de vigencia -- seguía en el conteo de Fase 1, 32
tests, y presentaba `BUG-02` como abierto pese a estar cerrado desde Fase 2),
`03_CODE_INVENTORY.md` (conteo de tests un paso atrás, y **describía como bug
activo un bug ya corregido**: el menú "Renombrar"/"Eliminar" sin conectar en
`MainActivity.kt`/`SampleRow.kt`, retirado desde Fase 1), y `README.md`
(apuntaba a `09_TEST_AUDIT.md` como fuente de tests; ahora apunta aquí).

**Gradle Wrapper**: sigue ausente. Este entorno de trabajo no tiene Gradle instalado ni acceso a red para generar o descargar un `gradle-wrapper.jar` legítimo. No se fabricó uno a mano (sería un binario sin verificación real). Limitación del entorno, documentada explícitamente, no resuelta.

## G. Riesgos abiertos

Ver `13_RISK_REGISTER.md`. Sin cambios de fondo respecto a la pasada anterior en cuanto a `R-02`/`R-08` (huecos de conocimiento del formato). Los riesgos de robustez de código que SÍ eran el objeto de Pass 3 (fragmentación MIDI, padding RIFF, blockAlign/byteRate, `0x01f7` malformado, MAX_ENTRIES) están corregidos en código y cubiertos por tests escritos (no ejecutados).

## H. DWP monolítico / FLAC

**Monolithic DWP implementation: NOT IMPLEMENTED.**
**FLAC implementation: NOT IMPLEMENTED.**

Confirmado por búsqueda exhaustiva en `app/src/main` (0 coincidencias de `0x0205`, `0x0206`, "flac", "embedded audio", "monolithic") como parte de la verificación final de esta pasada.

## I. Próxima fase

**Investigación controlada de DWP Monolithic y audio embebido mediante fixtures reales**, únicamente si el estado se confirma como verificado tras ejecución real de los 125 tests.

---

## Estado del Core: 🟡 CORE NOT YET VERIFIED

No se declara "CORE STABLE" porque, pese a que los 9 problemas identificados en la auditoría independiente de Pass 3 fueron corregidos con evidencia de código y tests escritos, **ningún test se ha ejecutado realmente**. El estado pasará a `CORE STABLE — READY FOR FINAL AUDIT` únicamente cuando se confirme una ejecución real (`gradle testDebugUnitTest`) con los 125 tests pasando.
