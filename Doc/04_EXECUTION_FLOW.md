# 04 — FLUJO DE EJECUCIÓN (paso a paso, con nombres de función reales)

## Flujo A: Cargar un proyecto

1. Usuario pulsa "LOAD" → `MainActivity` lanza `ActivityResultContracts.OpenDocument()`.
2. Callback recibe `Uri` → `viewModel.loadFromZip(uri)`.
3. `DwpCreatorViewModel.loadFromZip`:
   - `_uiState = Importing("Leyendo zip…")`
   - `contentResolver.openInputStream(uri).readBytes()` (en `Dispatchers.IO`)
   - `_uiState = Importing("Emparejando muestras…")`
   - `ZipProjectLoader.load(bytes)` (en `Dispatchers.Default`)
4. Dentro de `ZipProjectLoader.load`:
   - Descomprime todo el zip a un `LinkedHashMap<String, ByteArray>` en memoria (**sin streaming — carga todo el zip completo en RAM**, ver `07_AUDIO_PIPELINE_AUDIT.md` § memoria).
   - Ubica la primera entrada `*.dwp`.
   - `DwpDocument.parse(dwpBytes)` — verifica magic `"DwPr"`, extrae preámbulo de 90 bytes, tokeniza el resto y **exige** que el cursor final coincida exactamente con el tamaño del archivo (si no, lanza `DwpFormatException` — no hay parseo parcial silencioso a este nivel).
   - `DwpEngine.listSamples(document)` para saber qué `.wav` busca cada sample.
   - Empareja por nombre base (sin ruta/extensión, case-insensitive) contra las entradas `*.wav` del zip.
   - Si falta algún `.wav`, aborta con `ZipLoadException` (mensaje lista hasta 5 nombres faltantes).
5. Éxito → `project = loaded`; `publishLoaded(loaded)` calcula `instrumentName` (regex sobre nombres de sample) y `samples` (vía `listSamples`), y publica `DwpUiState.Loaded`.
6. Error en cualquier punto → `DwpUiState.Error(mensaje)`, mostrado como `AlertDialog` bloqueante (debe cerrarse explícitamente).

## Flujo B: Renombrar todo el instrumento

1. Usuario pulsa "RENOMBRAR" → `showRenameDialog = true` → `RenameAllDialog`.
2. Confirmar → `viewModel.renameAll(newName)`.
3. `detectInstrumentBaseName(document)` deduce el nombre viejo mediante regex `^(.*)_([A-G]#?-?\d+)_(\d+)$` sobre los nombres de sample, tomando el prefijo más frecuente.
4. `DwpEngine.renameInstrument(document, oldName, newName)`:
   - Recorre cada bloque top-level.
   - Si es `0x0003` (contenedor de sample) → tokeniza su payload **con `DwpTokenizer.tokenizeStrict()` desde Fase 1** (antes usaba la variante permisiva) y recursa sobre cada bloque interno. Si el payload anidado no se consume exactamente, lanza `DwpFormatException` en vez de reserializar una copia truncada (ver `16_PHASE1_CHANGES.md`).
   - Para cualquier bloque (top-level o interno): si `isTextPayload()` es verdadero **y** el texto contiene `oldName` como substring, hace `replace()` y reserializa solo ese bloque.
   - Todo bloque que no cumpla ambas condiciones se copia sin tocar, byte a byte.
5. `project = project.copy(document = renamedDoc)` — el array `audioByIndex` **no se toca** (el emparejamiento es por posición, no por nombre, documentado explícitamente en `LoadedProject.kt`).
6. `publishLoaded` vuelve a derivar `instrumentName`/`samples` desde el documento ya renombrado.

## Flujo C: Preview de un sample

1. Tap en una fila, **o Note On MIDI que caiga dentro de `it.lowKey..it.highKey` de algún sample** (`DwpCreatorViewModel.kt` línea 167: `samples.firstOrNull { event.note in it.lowKey..it.highKey } ?: return`) → `viewModel.previewSample(index, velocity)`.

   > **⚠️ BUG FUNCIONAL CONFIRMADO (`BUG-02`, ver `12_KNOWN_ISSUES.md`):** debido al intercambio de etiquetas en `0x01f4` (offset físico real es `root/low/high`, el código lee `low/root/high`), `sample.lowKey` para la muestra 0 contiene en realidad el valor de `rootKey` (36) en vez del verdadero límite inferior (0). Esto hace que el rango efectivo calculado sea `36..36` en vez de `0..36`. **Consecuencia real, no solo cosmética: las notas MIDI 0 a 35 no disparan ningún sample** en `Instrument.dwp`, porque la muestra 1 empieza en la nota 37. Este bug fue encontrado en la auditoría de la auditoría (no en la Fase 0 original) al verificar exhaustivamente todos los usos de `lowKey`/`rootKey` en el código, no solo su definición.

2. `samplePlayer.play(audioByIndex[index], velocity)` en un hilo de fondo:
   - `WavDecoder.decode` → si fallara, `SamplePlayer.play` **atrapa la excepción y retorna silenciosamente** (sin sonido, sin error visible al usuario) — documentado como riesgo de UX en `12_KNOWN_ISSUES.md`.
   - Conversión a PCM16 vía `PcmConverter` según el formato origen.
   - `AudioTrack` en `MODE_STREAM`, con verificación defensiva de `STATE_INITIALIZED`.
3. Flash visual de 250 ms en la tecla del piano (`_playingIndex`), independiente de cuándo termine realmente el audio (que sigue en su propio hilo/`AudioTrack`).

## Flujo D: Exportar

1. Usuario pulsa "EXPORT" → `ActivityResultContracts.CreateDocument("application/zip")` con nombre sugerido `"<instrumentName>.zip"`.
2. `viewModel.exportTo(uri)` → `_uiState = loadedState.copy(isExporting = true)`.
3. `ZipProjectExporter.export(project, newFolderName)`:
   - Verifica `samples.size == audioByIndex.size` (invariante interna) o lanza excepción de estado.
   - Para cada sample, en orden: decodifica su `.wav` real (`WavDecoder.decode`) y llama `DwpEngine.replaceSampleAudio` para recalcular **solo** el blob `0x01f7` (frameCount/channels/bytesPerSample/sampleRate/bits) con los valores reales del `.wav` que se va a escribir. El nombre/ruta del sample se pasa igual (ya reflejan un renombrado previo si lo hubo).
   - Escribe un nuevo zip: `"<folder>/<folder>.dwp"` + un `.wav` por sample, todos bajo la misma carpeta.
4. Escribe los bytes resultantes al `Uri` de salida elegido por el usuario.
5. Éxito → `isExporting = false`. Error → `DwpUiState.Error` conservando el `Loaded` previo para poder reintentar.

## Dónde se pierde o transforma información (evidencia, no suposición)

`[BINARY]`+`[CODE]` Verificado con el fixture real: el ciclo `parse → toBytes()` es **perfectamente sin pérdidas** cuando no hay edición (`bytes.contentEquals(rebuilt)` — cubierto por test y confirmado de forma independiente). Cuando SÍ hay edición (rename o replaceSampleAudio), la única información que cambia es exactamente la que se pretende cambiar; todo bloque no tocado se preserva íntegro, incluyendo duplicados de tag (verificado: el archivo real tiene tags duplicados como `0x006c`×2, `0x006d`×4, `0x0204`×16 dentro de cada sample, y sobreviven completos al ciclo porque el motor usa `.map` sobre la lista completa, nunca un `Map` por tag).
