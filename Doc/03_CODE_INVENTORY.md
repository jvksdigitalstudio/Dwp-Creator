# 03 — INVENTARIO TÉCNICO DE CÓDIGO

## Recuento fresco (actualizado en "Core Stabilization — Final Verification"; cifras de tests re-verificadas por conteo directo sobre el código actual)

| Categoría | Cantidad |
|---|---|
| Archivos totales del proyecto (incluyendo `Doc/`) | **69** (+1 con este propio documento de cierre en `Doc/`) |
| Archivos Kotlin (`.kt`) | **36** (31 en `main/`, 5 en `test/`) — sin cambios desde Pass 2, Pass 3 solo editó archivos existentes |
| Archivos Java/C/C++ | 0 |
| Métodos `@Test` totales | **125** (verificado por conteo directo `grep -c "@Test"`; era 97 al momento de Pass 2 — ver `Doc/18_CANONICAL_STATE.md` §E para el desglose actual por archivo) |
| Clases nuevas desde Pass 2 | Ninguna (`DwpVersionProfile.kt` y `MidiMessageParser.kt` ya existían; Pass 3 fue solo ediciones) |

`[CODE]` Todos los componentes fueron leídos completos (no por nombre/firma). Clasificación: KEEP / REFACTOR / REPLACE / REMOVE / BUG / PLACEHOLDER / UNKNOWN.

| Ruta | Módulo | Responsabilidad real (verificada) | Estado | Clasificación |
|---|---|---|---|---|
| `domain/dwp/DwpVersionProfile.kt` (nuevo) | dwp | Perfil de versión DWP (versión + tamaño de preámbulo); único perfil confirmado: v0x26 | Correcto, mínimo, sin especulación | **KEEP** |
| `domain/dwp/DwpBlock.kt` | dwp | Modelo físico de un bloque (tag/reserved/payload); detección de texto imprimible | Correcto en el modelo físico; KDoc de `0x01f4` corregido en pasada anterior | **KEEP** |
| `domain/dwp/DwpTokenizer.kt` | dwp | Tokenización/serialización genérica del stream de bloques (LE32); `tokenizeStrict()` desde Fase 1; `StopReason` estructurado desde Fase 2 | Correcto, distingue explícitamente EOF limpio/truncado/longitud inválida/overflow/límite de bloques | **KEEP** |
| `domain/dwp/DwpDocument.kt` | dwp | Parseo de preámbulo vía `DwpVersionProfile` (ya no un tamaño universal fijo) + verificación estricta de fin de archivo | Correcto y estricto (lanza excepción si no cuadra, o si la versión no está confirmada) | **KEEP** |
| `domain/dwp/DwpEngine.kt` | dwp | Renombrado recursivo (restringido a tags conocidos), patch de frame count, reemplazo de metadata de audio, listado de samples | `BUG-02` corregido en Fase 2; rename restringido a `RENAMABLE_TAGS`; validación de índice y de unicidad/tamaño de `0x01f7` añadida en Core Stabilization Pass 2 | **KEEP** |
| `domain/dwp/SampleInfo.kt` | dwp | DTO derivado, solo lectura, para la UI | Correcto, trivial | **KEEP** |
| `domain/audio/WavDecoder.kt` | audio | Parser RIFF/WAV: 8/16/24/32-bit PCM + float32 + EXTENSIBLE; ahora valida coherencia RIFF↔archivo real y estructura completa de EXTENSIBLE (cbSize+GUID) | Correcto y bien probado (28 tests, actualizado desde Pass 3 -- eran 21) | **KEEP** |
| `domain/audio/PcmConverter.kt` | audio | Conversión de cualquier formato soportado a PCM16 (solo para reproducción, NO para exportar al `.dwp`) | Correcto, probado | **KEEP** |
| `domain/io/ZipProjectLoader.kt` | io | Descompresión de zip con límites de recursos (`MAX_ENTRIES`/`MAX_SINGLE_ENTRY_BYTES`/`MAX_TOTAL_UNCOMPRESSED_BYTES`, verificados durante la lectura), emparejamiento `.dwp`↔`.wav` por nombre base | Correcto, probado con zip sintético | **KEEP** |
| `domain/io/ZipProjectExporter.kt` | io | Reconstrucción de zip con metadata de audio recalculada | Correcto, probado con ciclo completo load→rename→export→reload | **KEEP** |
| `domain/io/LoadedProject.kt` | io | DTO inmutable de proyecto en memoria | Trivial, correcto | **KEEP** |
| `audio/SamplePlayer.kt` | android-audio | Reproducción polifónica vía `AudioTrack` (siempre a PCM16) | Correcto; maneja defensivamente fallos de hardware | **KEEP** |
| `midi/MidiInputManager.kt` | android-midi | Listado/conexión de dispositivos MIDI; delega todo el parseo a `MidiMessageParser` | Correcto para el caso de uso (un solo dispositivo activo). `MidiManager.getDevices()` (deprecada en API 30) reemplazada por un helper `allDevices()` con gate de versión (`getDevicesForTransport` desde API 30, fallback documentado y suprimido explícitamente por debajo, ya que minSdk=26 no tiene otra API) -- corregido en Core Stabilization — Final Verification | **KEEP** |
| `midi/MidiMessageParser.kt` | midi (Kotlin/JVM puro) | Parseo MIDI: Note On/Off, running status correctamente acotado a mensajes de canal, System Realtime/Common reconocidos | Correcto, 29 tests dedicados (actualizado desde Pass 3 -- eran 18; no ejecutados aún en un entorno con Gradle real) | **KEEP** |
| `viewmodel/DwpCreatorViewModel.kt` | viewmodel | Orquestación de todo el flujo, único dueño del `StateFlow` de UI | Correcto y coherente; concentra algo de lógica de negocio (aceptable a esta escala) | **KEEP** |
| `MainActivity.kt` | ui | Composición raíz, launchers de `OpenDocument`/`CreateDocument`, pantalla de diagnóstico de crash | ⚠️ **CORREGIDO respecto a la versión anterior de este documento**: aquí se afirmaba "no conecta `onSampleRename`/`onSampleDelete` del `MainScreen`" (`BUG-01`). Verificado sobre el código actual: esos callbacks **ya no existen** -- fueron retirados (no implementados) al mitigar `BUG-01` en Fase 1, según `Doc/12_KNOWN_ISSUES.md`. No hay bug de callback sin conectar porque no hay callback | **KEEP** |
| `ui/screens/MainScreen.kt` | ui | Layout principal, lista de samples, diálogo de error | Correcto; ya no expone `onSampleRename`/`onSampleDelete` (retirados junto con el menú, ver fila de `SampleRow.kt`) | **KEEP** |
| `ui/components/SampleRow.kt` | ui | Fila de sample con menú contextual | ⚠️ **CORREGIDO**: la versión anterior de este documento decía que el menú ofrecía "Renombrar"/"Eliminar" sin funcionar. Verificado sobre el código actual: el menú **solo ofrece "Preview"** (real, funcional) -- las opciones rotas fueron eliminadas, no dejadas a medias. El propio código deja un comentario explícito citando `BUG-01`/`12_KNOWN_ISSUES.md` como referencia de por qué se retiraron en vez de implementarse | **KEEP** |
| `ui/components/DwpTopBar.kt`, `ImportingOverlay.kt`, `LoadEmptyState.kt`, `MidiDevicesDialog.kt`, `PianoKeyBadge.kt`, `RenameAllDialog.kt`, `StatusBar.kt` | ui | Componentes visuales puros, sin lógica de negocio | Correctos y cohesivos | **KEEP** |
| `ui/state/DwpUiState.kt` | ui | Máquina de estados sellada (Empty/Importing/Loaded/Error) | Correcto, buen diseño (evita estados imposibles) | **KEEP** |
| `ui/state/PreviewData.kt` | ui | Generador de datos falsos para preview de Compose (`@Preview`) | Explícitamente marcado como temporal en su propio comentario | **KEEP** (uso interno de desarrollo, no afecta producción) |
| `DwpCreatorApplication.kt` | app | Captura de excepciones no controladas a `SharedPreferences` para diagnóstico | Correcto; el propio comentario dice que es "andamiaje temporal" | **KEEP** (revisar antes de release, no durante Fase 0) |
| `ui/theme/*.kt` | ui | Colores/tipografía Compose | Sin lógica, sin riesgo | **KEEP** |
| Tests (`domain/**/*Test.kt`) | test | Ver `09_TEST_AUDIT.md` | Todos ejecutan lógica real contra fixtures reales o sintéticos válidos | **KEEP** |
| `app/src/test/resources/Instrument.dwp` | fixture | Único archivo binario de referencia real del proyecto | Verificado íntegramente en esta auditoría (script Python independiente) | **KEEP** (activo crítico — no debe borrarse ni sustituirse sin re-verificar todos los tests) |

## Componentes NO existentes (para que quede explícito y no se asuma nada)

`[CODE]` Búsqueda exhaustiva (`grep`/lectura completa) confirma que **no existen en el proyecto**, ni como esqueleto ni como placeholder:
- Ningún archivo relacionado con FLAC (`FlacEncoder`, `FlacDecoder`, `Streaminfo`, etc.)
- Ningún archivo relacionado con el tag `0x0206`.
- Ningún validador (`DwpStructureValidator`, `DwpAudioValidator`, etc.)
- Ninguna clase de modelo semántico (`DwpZone`, `Mapping`, `Filters`, `Envelope`, `LFO`, `ModulationMatrix`)
- Ningún parser/generador de audio monolítico embebido.
