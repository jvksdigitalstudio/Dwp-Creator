# 18 — ESTADO CANÓNICO ACTUAL DEL PROYECTO

Este documento es la única fuente de verdad agregada del proyecto en este momento. Cualquier otro documento de `Doc/` que lo contradiga debe explicar por qué está desactualizado respecto a este.

**Fecha de este estado:** actualizado en FASE 2 — Integración real de Monolithic DWP en el flujo de aplicación (`Doc/27_PHASE2_APP_INTEGRATION_REPORT.md`, 2026-09-20), sobre la base de FASE 1 (`Doc/26_PHASE1_FLAC_MONOLITHIC_INTEGRATION_REPORT.md`, misma fecha) y el Pass PRE-CI (`Doc/25_PRE_CI_AUDIT_AND_DOCUMENTATION_PASS.md`, 2026-09-17). **Las secciones E, H e I de más abajo se actualizan de nuevo en esta pasada** para reflejar que Monolithic DWP dejó de ser solo código de dominio auditado y pasó a estar conectado al flujo real de exportación de la app (nuevo orquestador `MonolithicDwpProjectBuilder`, nueva opción de exportación en la UI real). Donde algo cambió, se indica explícitamente qué decía antes y por qué se corrige, sin borrar la redacción histórica.
El texto original de esta cabecera (antes de este Pass) decía: *"tras 'Core Stabilization — Final Verification' (...). Supera y reemplaza la versión anterior (que reflejaba el estado tras `Doc/21_CORE_STABILIZATION_PASS3.md`). Sin cambios de comportamiento del motor DWP/WAV/MIDI en esta pasada; el alcance fue corrección de 2 warnings de compilación (§A), mejora del workflow de CI para reportar resultados reales de tests, y corrección de documentación desactualizada (...)."* Eso sigue siendo exacto **para el estado del Core**, que no cambió en el Pass PRE-CI (Sección 15/17 del prompt maestro de ese Pass: prohibido tocar el Core sin causa demostrable; no se encontró ninguna).

**Actualización `Doc/29` (2026-09-24, v0.4.0):** teclas de piano sin color por octava; 12 hallazgos de UI/estado/audio/memoria corregidos (H-01..H-12, ver `Doc/29` §2) y 6 abiertos documentados (A-01..A-06, §4). Se tocó la capa de I/O (`ZipProjectLoader.load(InputStream)`, `ZipProjectExporter.prepare`/`PreparedZipExport.writeTo`, ambas *añadiendo* API sin retirar la anterior) y la UI/ViewModel/`SamplePlayer`; **sin cambios** en `DwpEngine`/`DwpBlock`/`DwpDocument`/`DwpTokenizer`/`WavDecoder`/`domain/flac`/`domain/dwp/monolithic`. Tests presentes: **244** (215 hasta `Doc/28` + 22 de la primera parte de `Doc/29` §5 + 7 de la adenda de audio de baja latencia, `Doc/29` §8), **ninguno de esta pasada ejecutado todavía** (sin compilador en el entorno de trabajo).

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

**Nota de esta actualización (Pass PRE-CI, `Doc/25`):** hasta la versión anterior de este documento, esta sección decía "DWP Monolithic — NOT IMPLEMENTED", "FLAC — NOT IMPLEMENTED" y "`0x0205`/`0x0206` — sin código". Eso era cierto en ese momento (verificado entonces por búsqueda exhaustiva, 0 coincidencias) y dejó de serlo con `Doc/24_MONOLITHIC_DWP_FLAC_IMPLEMENTATION.md`: el subsistema fue escrito e integrado. Ver §H (reescrita) para el estado actual real de Monolithic DWP/FLAC — **implementado como código, todavía no validado por ejecución real (Gradle/CI)**, que es una categoría distinta tanto de "NOT IMPLEMENTED" como de "verificado".

Lo que sigue **sin implementar**, sin cambios respecto a la pasada anterior:

- `0x0205` — constante documentada en el código nuevo (`MonolithicDwpAudioBuilder.TAG_MONOLITHIC_AUDIO_UNSUPPORTED_ALT`), sin ningún writer para él (deliberado, fuera de alcance — Doc/23 §10, Doc/24 §3).
- Generación de un `.dwp` completo desde cero (siempre se parte de una plantilla real).
- Rename/delete de sample individual (UI oculta desde Pass 1, no implementada).
- Gradle Wrapper (documentado como limitación del entorno, no del proyecto — ver §F).
- LPC en el encoder FLAC (decisión deliberada de esta primera versión — ver §H; los predictores FIXED 0-4 producen FLAC válido y reversible, LPC queda fuera de alcance).
- Soporte de `PcmNormalizer` para PCM_32/FLOAT_32 (rechazados explícitamente — ver §H).

## C. UNKNOWN (campos DWP sin semántica demostrada)

Sin cambios respecto a la pasada anterior: `0x01f4` bytes 3-24, `0x01f7` offsets +04/+20/+24/+28/+32 (y ahora también +08/+0C/+10/+14/+18/+1C/+28/+2C nombrados explícitamente en el prompt de Pass 3 — ninguno tocado ni interpretado), `0x01f8`-`0x0203`, semántica de `0x0204`, contenido interno del preámbulo de 90 bytes, `0x0205`/`0x0206`.

## D. Código actual

**Actualizado en el Pass PRE-CI (`Doc/25`).** El texto anterior de esta sección ("31 archivos Kotlin `main` ..., 5 en `test`") describía el estado tras Core Stabilization Pass 3, antes de `Doc/24`. El estado real actual, contado directamente sobre el código (no por documentación):

- **50 archivos Kotlin de producción** (`app/src/main`): 31 del Core, sin cambios desde Pass 3 (ningún componente cambió de capa ni de responsabilidad; `MidiMessageParser` pasó de "parser sin memoria entre llamadas" a "máquina de estados incremental" en Pass 3, un cambio de comportamiento interno, no de arquitectura), más **19 archivos nuevos** de `Doc/24` (12 en `domain/flac/`, 7 en `domain/dwp/monolithic/`).
- **13 archivos Kotlin de test** (`app/src/test`): 5 del Core (sin cambios), más **8 archivos nuevos** de `Doc/24` (5 en `domain/flac/`, 3 en `domain/dwp/monolithic/`).

Ver §E para el conteo de métodos `@Test`.

## E. Tests actuales

**Actualizado tras el progreso real de exportación (UI premium, sin número de fase formal -- mejora puntual solicitada directamente).** Se añadió el parámetro `onProgress` a `ZipProjectExporter.export` y `MonolithicDwpProjectBuilder.build`/`buildBytes` (progreso real, no simulado, para `ExportProgressOverlay`), con 4 tests nuevos: 2 en `ZipProjectExporterProgressTest.kt` (nuevo archivo, contra el fixture real de 48 muestras) y 2 en `MonolithicDwpProjectBuilderTest.kt`. Estado real actual:

**215 métodos `@Test` en total, en 16 archivos** (211 previos + 4 nuevos).

**Core — 130, sin cambios.** **Monolithic DWP + FLAC + IO — 85** (81 previos + 4 nuevos).

**Estado de ejecución:** los 4 nuevos, igual que el resto desde `Doc/26`, no se han ejecutado en Gradle/CI real en este entorno.

**Anterior (`Doc/28`):** Esta fase añadió 5 tests a `PcmConverterTest.kt` (NaN/Infinity/-0.0 en el camino de previsualización), sin tocar ningún test existente, llevando el total de 206 a 211 (130 Core + 81 Monolithic/FLAC).

## F. Documentación actual

**Actualizado en el Pass PRE-CI (`Doc/25`):** esta sección decía "23
archivos en `Doc/` (00 a 22)", correcto en el momento de Core Stabilization
Final Verification. El estado real actual es **26 archivos en `Doc/` (00 a
25)**: `23_MONOLITHIC_DWP_FLAC_IMPLEMENTATION_SPEC.md` y
`24_MONOLITHIC_DWP_FLAC_IMPLEMENTATION.md` (especificación e implementación
de Monolithic DWP + FLAC) y `25_PRE_CI_AUDIT_AND_DOCUMENTATION_PASS.md`
(este Pass) se añadieron después. Texto histórico sin cambios a
continuación, referido al momento de `Doc/22`: documento nuevo de esa
pasada fue `22_CORE_STABILIZATION_FINAL_VERIFICATION.md`. Corregidos en esa
pasada por describir estado desactualizado o incorrecto respecto al código
real:
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

**Actualizado en FASE 1 (`Doc/26`).** El texto anterior de esta sección (Pass PRE-CI, `Doc/25`) decía "ESCRITO E INTEGRADO, VALIDACIÓN GRADLE/ANDROID PENDIENTE" para ambos subsistemas, correcto en ese momento (antes de que existiera ninguna corrida real de CI sobre este código). Desde entonces ocurrió una corrida real (`Doc/25` §19-20, runs #25 y #26: el primero no compiló, corregido; el segundo compiló y ejecutó los 186 tests con 4 fallos reales, también corregidos) y ahora esta fase (`Doc/26`) auditó de nuevo el código real completo de ambos subsistemas. Estado real actual:

**Monolithic DWP implementation: 🟡 ESCRITO, AUDITADO DOS VECES, PARCIALMENTE EJECUTADO EN CI REAL (run #26); LAS CORRECCIONES POSTERIORES A ESE RUN AÚN NO TIENEN UNA CORRIDA VERDE QUE LAS CONFIRME.**
**FLAC implementation (encoder + decoder de verificación): mismo estado, más un segundo bug real de producción encontrado y corregido en esta fase (ver abajo) y validación externa real contra `ffmpeg` (decoder FLAC independiente) para el algoritmo tal como está escrito en el Kotlin — ver `Doc/26` §8.**

- Código real en `domain/flac/` (12 archivos, sin archivos nuevos en esta fase) y `domain/dwp/monolithic/` (7 archivos, sin cambios en esta fase). Esta fase modificó 2 archivos de producción existentes (`FlacEncoder.kt`, `FlacFrameEncoder.kt`) y 1 archivo de test existente (`FlacEncoderRoundTripTest.kt`); no creó ni eliminó ningún archivo de código.
- 0 cambios en el Core (`DwpBlock`, `DwpDocument`, `DwpTokenizer`, `DwpVersionProfile`, `DwpEngine`, `WavDecoder`) en ninguna fase hasta la fecha; separación por composición mantenida.
- **Bug real de producción encontrado y corregido en esta fase:** `FlacEncoder.encode`/`FlacFrameEncoder.encodeFrame` aceptaban `blockSize` hasta `0x10000` (65536), pero `FlacMetadataWriter.StreamInfo` (STREAMINFO) solo puede representar `min/max_block_size` en campos de 16 bits SIN offset (0..65535, sección "METADATA_BLOCK_STREAMINFO" de la especificación pública de FLAC) — con suficientes muestras, esto producía un `IllegalArgumentException` opaco muy dentro del pipeline en vez de un rechazo claro en la frontera pública. Corregido a `1..0xFFFF` en ambos sitios, con mensaje explícito; 2 tests de regresión nuevos lo cubren (`FlacEncoderRoundTripTest`). Detalle completo en `Doc/26` §7.
- **Compatibilidad DirectWave/FL Studio: sigue NO DEMOSTRADA**, sin cambios respecto a `Doc/25`. No existe ningún `.dwp` Monolithic real de referencia; `0x0206` sigue 🔴 NO CONFIRMADO, `0x01f7`+12 sigue 🟡 PARTIALLY CONFIRMED. `0x0205` sigue sin código.
- **Predictores FIXED 0-4, sin LPC** — decisión deliberada, sin cambios en esta fase.
- `PcmNormalizer` sigue rechazando explícitamente `PCM_32`/`FLOAT_32`, sin cambios; esta fase sí añadió cobertura de 32-bit **a nivel del códec FLAC en sí** (`FlacEncoder`/`FlacPcmAudio`, que soportan `bitsPerSample` 4..32 independientemente de `PcmNormalizer`), dejando explícita la distinción entre ambas capas — ver `Doc/26` §8.
- **Validación externa real (nueva en esta fase, Sección 13 de su prompt maestro):** una reimplementación en Python del algoritmo leído en el Kotlin (`Crc`, `FlacBitWriter`, `FlacUtf8`, `FlacFixedPredictor`, `FlacRiceCoder`, `FlacFrameEncoder`, `FlacMetadataWriter`) generó streams `.flac` reales, decodificados bit-exacto por `ffmpeg 6.1.1` (decoder FLAC independiente, ajeno a este proyecto) para mono-16, stereo-8, stereo-24, mono-32 y los 6 boundaries de block size de la Sección 11. **Esto no es "el Kotlin se ejecutó"** — es evidencia de que el algoritmo, tal como está escrito en el código fuente Kotlin, produce un bitstream FLAC válido según un decoder real e independiente; sigue pendiente la ejecución del Kotlin real en Gradle/CI. Hallazgo adicional honesto: `ffmpeg`/libavcodec rechaza streams con `blockSize < 16` ("invalid max blocksize") aunque el decoder propio de este proyecto los acepta sin problema (auto-consistencia interna, no compatibilidad real) — documentado como riesgo de interoperabilidad no confirmado, sin modificar el códec por falta de evidencia de que DirectWave lo requiera. Ver `Doc/26` §8 para el detalle completo, incluyendo comandos y resultados exactos.
- **Tests:** 81 métodos `@Test` en 9 archivos (76 tras `Doc/26` + 5 nuevos de `MonolithicDwpProjectBuilderTest.kt`), **0 ejecutados** en Gradle/CI real hasta la fecha de este documento (ver §E).
- `0x01f7` offset +12 ("bytes por sample"): sin cambios, sigue 🟡 PARTIALLY CONFIRMED. **Actualizado en FASE 2.1 (`Doc/28`):** lo mismo aplica a offset +36 ("bits por sample") y, en general, a la caracterización "float32" del fixture real -- que documentos anteriores (`Doc/05`, `Doc/07`, `Doc/23`) presentaban con más certeza de la que la evidencia disponible hoy respalda. `Doc/28` reclasifica esa caracterización como 🟡 PARTIALLY CONFIRMED (evidencia histórica real pero no re-verificable, no una observación de esta sesión) y corrige el KDoc/mensajes de excepción de `PcmNormalizer.kt` en consecuencia, sin cambiar su comportamiento (sigue rechazando `PCM_32`/`FLOAT_32` explícitamente, decisión que sigue siendo correcta).

**Actualizado en FASE 2 (`Doc/27`).** Hasta esta fase, Monolithic DWP existía como código de dominio auditado pero **desconectado del flujo real de la aplicación** (ningún botón, pantalla ni ViewModel lo invocaba; solo `ZipProjectExporter`/`exportTo` generaban el `.zip` clásico). Esta fase lo conecta: nuevo orquestador `MonolithicDwpProjectBuilder` (recorre **todas** las muestras de un `LoadedProject` real, no una sola), nueva función `DwpCreatorViewModel.exportMonolithicTo(uri)` y un nuevo diálogo de elección de formato (`ExportFormatDialog`) en el flujo de exportación real de `MainActivity`. El usuario ahora puede generar un `.dwp` autocontenido (audio embebido, sin `.wav` externos) desde la app misma -- explícitamente etiquetado en la UI como "experimental", con la falta de confirmación de compatibilidad DirectWave/FL Studio comunicada directamente, no ocultada. Ver `Doc/27` para el detalle completo de la auditoría de arquitectura, decisión de integración y tests.

## I. Próxima fase

**Actualizado en FASE 2 (`Doc/27`).** La redacción anterior (`Doc/26`) pedía una corrida de CI real que confirmara las correcciones de esa fase; sigue pendiente, ahora también cubriendo los 5 tests nuevos de `Doc/27`. Con Monolithic DWP ya conectado al flujo real de la app, la próxima fase real es:

**Nueva ejecución de CI mediante GitHub Actions sobre el proyecto íntegro** (`gradle testDebugUnitTest` + `gradle assembleDebug`), cubriendo los 206 `@Test` actuales (125 Core + 81 Monolithic+FLAC) en conjunto. Después: compilar el APK real, generar un `.dwp` autocontenido desde la app (con el nuevo diálogo de exportación) y probarlo en DirectWave/FL Studio real -- es la única forma de mover `0x0206` de UNCONFIRMED a CONFIRMED o PARTIALLY CONFIRMED con evidencia real, y de decidir con datos si el `.dwp` autocontenido deja de ser "experimental" en la UI.

---

## Estado del Core: 🟢 CORE STABLE — VERIFIED BY REAL CI EXECUTION

Confirmado con evidencia real de GitHub Actions (run #24, "Migración de identidad: Dwp Creator", `succeeded`), no por inspección estática de código: `gradle testDebugUnitTest` → `BUILD SUCCESSFUL in 38s`, **125/125 tests pasando, 0 fallidos, 0 omitidos** (parseado directamente de los reportes JUnit XML reales, ver `.github/workflows/build.yml` step "Summarize test results"); `gradle assembleDebug` → APK generado y subido como artifact; 0 warnings del compilador Kotlin (los 2 warnings de `MidiDeviceInfo`/`darkTheme` corregidos en Core Stabilization — Final Verification ya no aparecen). Detalle completo, incluyendo un error real introducido y corregido en el proceso, en `Doc/22_CORE_STABILIZATION_FINAL_VERIFICATION.md` §16-19.

RISK-02 y R-08 permanecen deliberadamente **ABIERTOS** (fuera del alcance de esta verificación — requieren un segundo `.dwp` de referencia real). En el momento de esta verificación (tras `Doc/22`), Monolithic DWP y FLAC estaban confirmados **NO implementados**, como exigía el alcance de esa fase.

**Nota de vigencia (Pass PRE-CI, `Doc/25`):** este párrafo describe correctamente y sin cambios el estado del **Core** — sigue siendo 🟢 estable y verificado por CI real, y no fue tocado en el Pass PRE-CI. Ya **no** describe el estado agregado del proyecto completo: Monolithic DWP + FLAC pasaron de "no implementado" a "escrito e integrado, pendiente de validación real" — ver §H arriba. El estado agregado actual del proyecto completo (Core + Monolithic + FLAC) está en `Doc/25_PRE_CI_AUDIT_AND_DOCUMENTATION_PASS.md`.
