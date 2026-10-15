# 19 — FASE 2: AUDITORÍA, CORRECCIÓN Y CONSOLIDACIÓN DEL CORE

Este documento registra, con trazabilidad completa, todos los cambios de código realizados en esta fase, siguiendo el prompt maestro "Auditoría, Corrección y Consolidación Profesional del Core". A diferencia de la Fase 1 (2 correcciones menores), esta fase corrige **9 problemas reales** identificados en la propia auditoría anterior (`Doc/12_KNOWN_ISSUES.md`, `Doc/13_RISK_REGISTER.md`) más varios adicionales encontrados durante la auditoría exhaustiva exigida por este prompt. **No se implementó nada de Monolithic DWP, FLAC, `0x0205`/`0x0206`** — eso sigue bloqueado hasta la fase de investigación explícitamente señalada como siguiente paso.

## A. Estado inicial (qué había realmente, antes de tocar código)

- Núcleo binario DWP funcional pero con `BUG-02` confirmado: `DwpEngine.listSamples()` intercambiaba las etiquetas `lowKey`/`rootKey`, con consecuencia funcional real (rotura de selección MIDI para el rango bajo del teclado).
- `renameInstrument()` usaba heurística de "cualquier bloque que decodifique como texto imprimible", sin restringir a tags conocidos — riesgo estructural documentado (`RISK-01`) pero no corregido.
- `DwpTokenizer` distinguía razones de parada solo implícitamente (un único `break`), sin overflow-safety en la aritmética de límites.
- `WavDecoder` no validaba `dataSize % bytesPerFrame == 0`, ni protegía contra overflow de enteros en el bucle de chunks RIFF, ni validaba el tamaño mínimo del chunk `fmt `.
- `ZipProjectLoader` aceptaba silenciosamente el primer `.dwp` si había varios, y sobrescribía silenciosamente colisiones de nombre base de `.wav` entre carpetas distintas (ambos casos explícitamente prohibidos por el prompt).
- `ZipProjectExporter` construía rutas de zip sin sanitizar `newFolderName` ni `sample.name` — vulnerable a path traversal si un `.dwp` de origen contenía un nombre de sample malicioso.
- `DwpEngine.patchFrameCount`/`replaceSampleAudio` no validaban el índice de sample recibido — un índice fuera de rango caía en un no-op silencioso.
- `MidiInputManager` no soportaba *running status* (una omisión real de la Sección 16), descartando en silencio notas MIDI en flujos que lo usan.
- `SamplePlayer` no validaba `channelCount` (trataba cualquier valor ≠1 como estéreo) ni limitaba el número de voces concurrentes.

## B. Problemas encontrados y corregidos

| ID | Problema | Severidad | Archivo | Estado |
|---|---|---|---|---|
| P-01 | `BUG-02`: etiquetas `lowKey`/`rootKey` intercambiadas en `0x01f4`, con impacto funcional real en selección MIDI | CRITICAL | `DwpEngine.kt`, `DwpBlock.kt` | **CORREGIDO** |
| P-02 | Rename inseguro: cualquier bloque "que parezca texto" era editable | HIGH | `DwpEngine.kt` | **CORREGIDO** (allow-list de 4 tags conocidos) |
| P-03 | Tokenizer sin razones de parada estructuradas; aritmética de límites sin protección de overflow | MEDIUM | `DwpTokenizer.kt` | **CORREGIDO** |
| P-04 | `WavDecoder`: sin validación `dataSize % bytesPerFrame`, sin protección de overflow en bucle RIFF, sin validar tamaño mínimo de `fmt ` | HIGH | `WavDecoder.kt` | **CORREGIDO** |
| P-05 | `ZipProjectLoader`: múltiples `.dwp` aceptados en silencio (toma el primero) | HIGH | `ZipProjectLoader.kt` | **CORREGIDO** (rechaza el proyecto) |
| P-06 | `ZipProjectLoader`: colisión de nombre base de `.wav` entre carpetas sobrescribía en silencio dentro de un mapa | HIGH | `ZipProjectLoader.kt` | **CORREGIDO** (rechaza el proyecto) |
| P-07 | `ZipProjectExporter`: sin sanitización de nombres, riesgo de path traversal (`../`) vía un `sample.name` malicioso | HIGH | `ZipProjectExporter.kt` | **CORREGIDO** |
| P-08 | `ZipProjectExporter`: sin detección de nombres de sample duplicados (colisión silenciosa de entradas de zip) | MEDIUM | `ZipProjectExporter.kt` | **CORREGIDO** |
| P-09 | `patchFrameCount`/`replaceSampleAudio`: índice fuera de rango caía en no-op silencioso | MEDIUM | `DwpEngine.kt` | **CORREGIDO** (`IllegalArgumentException` explícita) |
| P-10 | `MidiInputManager`: sin soporte de running status — notas descartadas en silencio en streams que lo usan | MEDIUM | `MidiInputManager.kt` | **CORREGIDO** |
| P-11 | `SamplePlayer`: `channelCount` distinto de 1/2 tratado silenciosamente como estéreo (audio corrupto sin aviso) | MEDIUM | `SamplePlayer.kt` | **CORREGIDO** (se rechaza la reproducción) |
| P-12 | `SamplePlayer`: sin límite de voces concurrentes (riesgo de agotar `AudioTrack` del sistema) | LOW | `SamplePlayer.kt` | **CORREGIDO** (cap defensivo de 24 voces, no voice-stealing) |

## C. Correcciones realizadas (archivo por archivo)

### `domain/dwp/DwpBlock.kt`
Corregido el KDoc de `0x01f4` (root/low/high, no low/root/high) — el propio comentario del código tenía el mismo error que la documentación externa.

### `domain/dwp/DwpEngine.kt`
1. `listSamples()`: intercambiadas las asignaciones de `rootKey`/`lowKey` para reflejar el offset físico real.
2. `renameInstrument()`/`renameRecursive()`: añadido `RENAMABLE_TAGS` (allow-list de `0x0066`, `0x0067`, `0x01f5`, `0x01f6`); cualquier otro bloque se copia intacto sin importar su contenido.
3. `patchFrameCount()`/`replaceSampleAudio()`: añadida `requireValidSampleIndex()` al inicio de ambas funciones.
4. Nueva función pública `sampleCount(doc)`.

### `domain/dwp/DwpTokenizer.kt`
1. Nuevo `enum StopReason` (`CLEAN_EOF`, `TRAILING_BYTES`, `INVALID_LENGTH`, `PAYLOAD_TRUNCATED`, `MAX_BLOCKS_REACHED`), añadido a `TokenizeResult`.
2. Aritmética de límites (`payloadStart + length > n`) movida a `Long` para evitar overflow con una longitud declarada cercana a `Int.MAX_VALUE`.
3. `tokenizeStrict()` ahora reporta la razón de parada específica en el mensaje de excepción.

### `domain/dwp/DwpDocument.kt`
`parse()` actualizado para usar la nueva `StopReason` en su mensaje de error (diagnóstico más preciso, mismo comportamiento de excepción).

### `domain/audio/WavDecoder.kt`
1. Bucle de chunks RIFF: rechaza `size < 0` explícitamente; aritmética de límites movida a `Long` (mismo patrón que el tokenizer DWP); valida que el chunk `fmt ` tenga al menos 16 bytes antes de leer sus campos.
2. Nueva validación: `dataSize % bytesPerFrame == 0`, rechazando en vez de truncar en silencio vía división entera.

### `domain/io/ZipProjectLoader.kt`
1. Detecta y rechaza entradas de zip con nombre exactamente duplicado.
2. Rechaza el proyecto si hay más de un `.dwp` (antes tomaba el primero en silencio).
3. Detecta colisión de nombre base de `.wav` entre carpetas distintas y rechaza con un mensaje que lista las rutas en conflicto (antes, `associate` sobrescribía en silencio).
4. Soporta tanto `/` como `\` como separador de ruta al calcular el nombre base.

### `domain/io/ZipProjectExporter.kt`
1. Nueva función `requireSafeZipSegment()`: rechaza nombres vacíos, con `/`/`\`, o iguales a `.`/`..`.
2. Aplicada a `newFolderName` y a cada `sample.name` antes de construir cualquier `ZipEntry`.
3. Detecta y rechaza nombres de sample duplicados antes de escribir nada.

### `midi/MidiInputManager.kt`
1. Añadido soporte de *running status*: un byte sin el bit alto activado se interpreta como dato del último status real visto (persistido en `runningStatus`, reseteado en cada `connectTo`/`disconnect`).
2. Manejo seguro de mensajes Note On/Off incompletos al final del buffer (se detiene en vez de reinterpretar un byte de datos suelto como un nuevo status).

### `audio/SamplePlayer.kt`
1. Rechaza reproducir si `channelCount !in 1..2` (antes, cualquier valor ≠1 se trataba como estéreo).
2. Añadido `MAX_CONCURRENT_VOICES = 24` como tope defensivo simple (no voice-stealing).

## D. Tests

**Total: 54 métodos `@Test`** (antes: 32). Todos los tests nuevos se añadieron junto a los existentes, sin eliminar ninguno; los que asumían el comportamiento del bug (`BUG-02`) fueron corregidos, no borrados.

| Test | Estado | Motivo |
|---|---|---|
| Los 32 tests existentes de Fase 0/1 | **TEST WRITTEN**, actualizados donde el bug corregido cambiaba el resultado esperado (2 tests de `DwpEngineTest` corregidos: `key range...` y `renameInstrument replaces name everywhere...`) | Ver detalle abajo |
| 6 tests nuevos en `DwpEngineTest.kt` (key range corregido, selección MIDI por nota, rename restringido a tags conocidos, validación de índice ×2, `sampleCount`) | **TEST WRITTEN** | Cubren directamente P-01, P-02, P-09 |
| 8 tests nuevos de `DwpTokenizer` en `DwpEngineTest.kt` (`StopReason` × 5 casos + mensaje de error) | **TEST WRITTEN** | Cubren P-03 |
| 5 tests nuevos en `WavDecoderTest.kt` (dataSize no múltiplo, fmt pequeño, chunk excede archivo, overflow cerca de Int.MAX, size negativo) | **TEST WRITTEN** | Cubren P-04 |
| 5 tests nuevos en `ZipProjectIoTest.kt` (múltiples .dwp, basename ambiguo, path traversal en carpeta, path traversal en sample, nombres duplicados) | **TEST WRITTEN** | Cubren P-05, P-06, P-07, P-08 |

**TEST EXECUTED / TEST PASSED: NO se pudo verificar en este entorno** (sin Android SDK/Gradle disponible en el sandbox de esta sesión, misma limitación ya declarada en `10_BUILD_AUDIT.md`). Todo lo anterior es **TEST WRITTEN**, verificado por lectura completa y razonamiento manual contra la implementación exacta, no por ejecución real. Se recomienda ejecutar `gradle testDebugUnitTest` en GitHub Actions antes de continuar, y reportar aquí el resultado real.

**No se escribió ningún test para el soporte de running status de `MidiInputManager` (P-10)** ni para la validación de canales/límite de voces de `SamplePlayer` (P-11/P-12): ambas clases dependen de `android.media.midi`/`android.media.AudioTrack`, no instanciables en un test JVM puro sin Robolectric (ausente del proyecto). Esto se declara explícitamente como **TEST NOT WRITTEN**, no se oculta ni se finge cobertura.

## E. Documentación actualizada

- `Doc/00_PROJECT_AUDIT.md`: BUG-02 marcado como corregido (revisar en próxima pasada).
- `Doc/12_KNOWN_ISSUES.md`, `Doc/13_RISK_REGISTER.md`: estados de P-01 a P-12 actualizados a CERRADO donde corresponde.
- `Doc/14_KEEP_REFACTOR_REPLACE.md`: `DwpEngine` reclasificado.
- `Doc/18_CANONICAL_STATE.md`: actualizado como estado agregado vigente.
- `README.md`: corregido para eliminar la descripción de "esqueleto/Paso 1 de 7" (ver Sección F más abajo).
- Este documento (`19_PHASE2_CORE_HARDENING.md`), nuevo.

## F. Problemas que permanecen (nada se oculta)

- **`RISK-02`** (offsets de `0x01f7` no verificados para 16-bit PCM): sigue abierto — requiere un segundo `.dwp` de referencia real, no resoluble solo con código.
- **`R-08`** (sesgo de fixture único, campos de loop en `0x01f7` sin evidencia): sigue abierto, misma razón.
- **Renombrar/eliminar sample individual**: sigue sin implementar (mitigado en Fase 1 ocultando la UI rota, no implementado).
- **Gradle Wrapper ausente**: sigue sin generarse (INFO, no bloqueante).
- **No hay tests para `MidiInputManager`/`SamplePlayer`** por la limitación de JVM puro ya explicada — declarado, no oculto.
- **No se pudo ejecutar la suite de tests en este entorno** — declarado explícitamente en la Sección D, no se afirma "tests passed" sin haberlo verificado.
- El *rename* ahora restringido a 4 tags conocidos: si en el futuro se confirma que otro tag adicional también es un campo de nombre/ruta legítimo, habrá que añadirlo explícitamente a `RENAMABLE_TAGS` — hoy, cualquier otro campo de texto NO se renombra aunque debiera (comportamiento conservador intencional, documentado).

## G. Decisiones de arquitectura

- **No se cambiaron APIs públicas de forma incompatible**: todas las funciones de `DwpEngine`/`WavDecoder`/`ZipProjectLoader`/`ZipProjectExporter` mantienen su firma; los cambios son de comportamiento interno (validaciones más estrictas) o de excepciones lanzadas ante casos que antes fallaban en silencio.
- **`TokenizeResult` ganó un campo (`stopReason`)**: cambio compatible hacia atrás para cualquier código que acceda por nombre (`.blocks`, `.endOffset`); se verificó que ningún código del proyecto usa destructuring posicional sobre este tipo.
- **No se introdujo ninguna dependencia nueva.**
- **No se tocó nada de `0x0205`/`0x0206`/FLAC/Monolithic DWP**, ni se creó ningún componente especulativo para ello — se respetó estrictamente la Sección 23 del prompt maestro.
- **El parser físico (`DwpTokenizer`/`DwpBlock`) sigue completamente separado del motor semántico (`DwpEngine`)** — la corrección de `BUG-02` fue quirúrgica, sin necesidad de tocar el modelo físico.

## H. Estado del Core

**🟡 READY FOR NEXT RESEARCH PHASE — con una condición pendiente.**

No se declara "production ready": persisten `RISK-02` y `R-08` (huecos de conocimiento del formato, no de calidad de código), la suite de tests no se ha ejecutado realmente en este entorno, y hay funcionalidad de UI pendiente (rename/delete individual). Sin embargo, los 12 problemas de robustez/corrección identificados en esta auditoría **fueron corregidos con evidencia y tests escritos** (aunque no ejecutados), y no se identificó ningún problema adicional de severidad CRITICAL o HIGH sin abordar dentro del alcance de esta fase.

**Condición para pasar a "LISTO SIN RESERVAS"**: confirmar en un entorno con Gradle real que los 54 tests compilan y pasan.

## I. Próxima fase

Tal como especifica el prompt maestro: **investigación controlada del formato DWP Monolithic y audio embebido**, incluyendo nuevos fixtures reales y análisis de `0x0205`/`0x0206`/FLAC. No se implementa en esta sesión.
