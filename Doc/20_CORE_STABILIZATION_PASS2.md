# 20 — CORE STABILIZATION PASS 2

Registro completo de esta fase, siguiendo el prompt maestro "CORE STABILIZATION PASS 2". Continúa directamente sobre `Doc/19_PHASE2_CORE_HARDENING.md`, cerrando los puntos técnicos que esa fase dejó explícitamente abiertos o que un reexamen más profundo reveló.

## 1. Auditoría inicial (qué se encontró antes de tocar código)

- `patchFrameCount()`/`replaceSampleAudio()` **no validaban** que existiera exactamente un `0x01f7` de 40 bytes antes de escribir: un `0x01f7` duplicado se parcheaba dos veces en silencio; uno ausente hacía que la función completara sin error y sin cambios; uno de tamaño incorrecto llegaba a `writeLE32` sin ninguna comprobación previa.
- El running status MIDI, corregido en la Pass 1, tenía un bug real no detectado entonces: **cualquier byte de estado (incluidos mensajes de sistema como MIDI Clock, `0xF8`) actualizaba el running status**, cuando la Sección 8 de este prompt exige que solo los mensajes de canal (`0x80`-`0xEF`) lo hagan. Además, la lógica vivía inline en una clase Android (`MidiInputManager`), sin ninguna cobertura de test JVM.
- `DwpDocument.parse()` trataba `PREAMBLE_SIZE = 90` como una constante universal, sin leer ni validar el campo de versión real del archivo (offset 4-7, confirmado por evidencia binaria como `0x26`).
- `WavDecoder` nunca leía ni validaba el tamaño RIFF declarado (offset 4-7) — solo usaba el tamaño físico del `ByteArray`, ignorando por completo la relación `riffSize ↔ archivo real` que exige la Sección 15/16. La validación de `WAVE_FORMAT_EXTENSIBLE` tampoco comprobaba `cbSize` ni el GUID completo, solo sus 2 primeros bytes.
- `ZipProjectLoader` no tenía ningún límite de recursos: ni cantidad de entradas, ni tamaño de una entrada individual, ni tamaño total descomprimido.
- `versionName` seguía siendo `"0.1.0-skeleton"` pese a que el README ya se había corregido en la Pass 1.

## 2. Cambios realizados (por archivo)

| Archivo | Problema | Cambio | Motivo |
|---|---|---|---|
| `DwpEngine.kt` | Sin validación de "exactamente un 0x01f7 de 40 bytes" | Nuevo helper `requireSingleAudioFormatBlock()`, llamado **antes** de construir cualquier lista parcheada en `patchFrameCount`/`replaceSampleAudio` | Atomicidad real: la validación ocurre antes de tocar un solo byte, no a mitad de la mutación |
| `DwpBlock.kt` | — | Sin cambios de contenido; sirve de referencia para `TAG_AUDIO_FORMAT` usado en el nuevo helper | — |
| `DwpDocument.kt` | `PREAMBLE_SIZE=90` tratado como universal | `parse()` ahora lee el campo de versión (offset 4, LE32), busca su `DwpVersionProfile`, y usa `profile.preambleSize` en vez de una constante global. Versión no reconocida → `DwpFormatException` explícita, sin inventar layout | Sección 10-13 del prompt maestro |
| `DwpVersionProfile.kt` (nuevo) | — | Clase con un único perfil confirmado: `V0x26` (versión `0x26`, preámbulo 90 bytes) | Desacopla "lo que sabemos de una versión" de "lo que el parser asume siempre" |
| `WavDecoder.kt` | RIFF size nunca validado; extensible con validación incompleta | Lee `riffSize`, calcula `effectiveEnd = 8 + riffSize`, usa ese límite (no `bytes.size`) en todo el parsing; rechaza truncamiento, tolera bytes sobrantes como padding. `WAVE_FORMAT_EXTENSIBLE`: valida `cbSize==22`, tamaño mínimo del chunk, y el sufijo GUID completo (14 bytes) contra la constante `KSDATAFORMAT_SUBTYPE_SUFFIX` | Sección 15-19 |
| `ZipProjectLoader.kt` | Sin límites de recursos | `MAX_ENTRIES=2000`, `MAX_SINGLE_ENTRY_BYTES=200MB`, `MAX_TOTAL_UNCOMPRESSED_BYTES=1GB`, documentados con su justificación; nueva `readBytesLimited()` que corta la lectura **durante** la descompresión, no después | Sección 20-22; defensa real contra "zip bomb" |
| `MidiInputManager.kt` | Lógica MIDI inline, no testeable; bug de running status en mensajes de sistema | Toda la lógica de parseo delegada a la nueva clase `MidiMessageParser` | Sección 6 |
| `MidiMessageParser.kt` (nuevo) | — | Parser MIDI puro Kotlin/JVM. Corrige el bug: solo `0x80`-`0xEF` establecen running status; `0xF8`-`0xFF` (System Realtime) son transparentes; `0xF0`-`0xF7` (System Common) cancelan el running status | Sección 7-8 |
| `app/build.gradle.kts` | `versionName` decía "skeleton" | `"0.3.0-core-stabilization"` | Sección 29 |
| `README.md` | Sin cambios en esta pasada (ya corregido en Pass 1) | — | — |

## 3. Tests añadidos/modificados

| Archivo | Tests nuevos | Qué verifican |
|---|---|---|
| `DwpEngineTest.kt` | 14 nuevos (39 total, antes 25) | Preservación de bloques desconocidos tras rename (Sección 26); atomicidad explícita de `renameInstrument`/`patchFrameCount` ante un contenedor corrupto (Sección 5); rechazo de `0x01f7` ausente/duplicado/tamaño incorrecto en ambas funciones (Secciones 3-4); reconocimiento de `DwpVersionProfile` v0x26, rechazo de versión desconocida, preámbulo truncado, archivo demasiado pequeño (Secciones 10-14) |
| `WavDecoderTest.kt` | 7 nuevos (21 total, antes 14) | Tolerancia a bytes sobrantes más allá de RIFF; rechazo de archivo truncado antes de completar el tamaño RIFF declarado; rechazo de tamaño RIFF negativo; rechazo de chunk que excede el límite RIFF aunque quepa físicamente en el array; `cbSize` inesperado en extensible; GUID de sub-formato no reconocido; `fmt` extensible demasiado pequeño para `cbSize` |
| `ZipProjectIoTest.kt` | 4 nuevos (13 total, antes 9) | Rechazo por exceso de entradas; aceptación correcta en el límite exacto (sin falso positivo); rechazo de una entrada individual demasiado grande; rechazo de total descomprimido excesivo aunque ninguna entrada individual lo sea |
| `MidiMessageParserTest.kt` (nuevo) | 18 tests | Suite completa exigida por la Sección 9: Note On/Off normal, velocity 0, running status (simple, múltiple, entre llamadas separadas, reset), límites 0/127 de nota y velocidad, canal 0 y 15, mensaje truncado (normal y en running status), byte inesperado sin estado previo, mensaje de canal no soportado (CC) sin interferir, **System Realtime no corrompe running status**, **System Common cancela running status**, recuperación tras un byte de estado inusual |

**Total: 97 métodos `@Test`** (antes de esta pasada: 54). Recontado con `grep -c "@Test"` por archivo, no reciclado de ningún documento anterior.

**Regresión verificada por lectura y trazado manual, no por ejecución** (ver Sección 5 de este documento): se confirmó explícitamente que `buildWav`/`buildExtensibleWav`/`placeholderAudio` (helpers de test ya existentes) ya calculaban `riffSize` correctamente, por lo que el endurecimiento de `WavDecoder` no rompe ningún test previo — salvo uno: `buildExtensibleWav` escribía un sufijo de GUID de ceros, que la nueva validación de GUID habría rechazado. Se corrigió el helper para escribir el sufijo `KSDATAFORMAT_SUBTYPE` real (la corrección correcta: el fixture de test debe ser representativo de un archivo real, no al revés).

## 4. Tests no ejecutados

**Ninguno de los 97 tests fue ejecutado en este entorno.** No hay Android SDK ni Gradle disponibles en este sandbox (misma limitación declarada desde `Doc/10_BUILD_AUDIT.md`). Toda la verificación de esta fase fue:
1. Lectura completa de cada archivo tras cada edición.
2. Trazado manual paso a paso de los casos más complejos (ej. el cálculo exacto de `effectiveEnd` en el test de "chunk beyond RIFF boundary", verificado offset por offset).
3. Verificación de que los helpers de construcción de fixtures en los tests ya existentes seguían siendo compatibles con la lógica nueva, no solo que los tests nuevos "se ven correctos".

Se recomienda, antes de continuar a la siguiente fase, ejecutar `gradle testDebugUnitTest` en GitHub Actions y confirmar el resultado real.

## 5. Problemas que permanecen (nada se oculta)

- `RISK-02`/`R-08` (offsets `0x01f7` no verificados para 16-bit, sesgo de fixture único): **sin cambios**, siguen bloqueados por falta de un segundo `.dwp` de referencia real — no resolubles con código.
- `RISK-03` (copy engañoso en `LoadEmptyState` sobre carga de `.dwp` suelto): no tocado, fuera del alcance de esta fase.
- `UX-01` (fallo silencioso de `SamplePlayer.play()` ante WAV corrupto en preview): no tocado.
- Rename/delete individual de sample: sigue sin implementar (UI oculta desde Pass 1).
- Gradle Wrapper: sigue ausente.
- **Ningún test para `MidiMessageParser` se pudo ejecutar realmente** — están escritos y son JVM puros (ya no dependen de Android), pero siguen sin ejecución real confirmada en este entorno.
- El preámbulo de 90 bytes de la versión `0x26` sigue siendo una caja opaca — `DwpVersionProfile` desacopla el TAMAÑO del preámbulo por versión, pero no decodifica su CONTENIDO interno (fuera de alcance, nunca se ha necesitado).

## 6. Estado del Core

**CORE STABLE — READY FOR NEXT RESEARCH PHASE**

Justificación: los 6 puntos de "Prioridad 1-6" de este prompt fueron auditados y corregidos con evidencia y tests escritos; la atomicidad de `renameInstrument`/`patchFrameCount`/`replaceSampleAudio` está demostrada explícitamente (no solo inferida); el parser MIDI es ahora Kotlin/JVM puro con 18 tests dedicados; el versionado DWP ya no asume un preámbulo universal; WAV valida la coherencia RIFF↔archivo real y la estructura completa de `WAVE_FORMAT_EXTENSIBLE`; ZIP tiene límites de recursos documentados y verificados durante la lectura, no después. Ningún problema de severidad CRITICAL permanece. Los riesgos HIGH restantes (`R-03`, `R-08`) son de conocimiento del formato — exactamente el objeto de la siguiente fase, no del Core.

**Condición que sigue pendiente para un "sin reservas" total**: ejecutar los 97 tests en un Gradle real y confirmar que pasan.

## 7. Próxima fase

Tal como exige la Sección 36.7 del prompt maestro: **investigación controlada de DWP Monolithic y audio embebido mediante fixtures reales**. No implementada en esta sesión.
