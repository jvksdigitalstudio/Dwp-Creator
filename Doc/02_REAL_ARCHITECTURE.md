# 02 — ARQUITECTURA REAL (reconstruida, no idealizada)

`[CODE]` Flujo real verificado leyendo cada llamada, desde la UI hasta el byte final escrito:

```
MainActivity (Compose)
  │  ActivityResultContracts.OpenDocument()  -> Uri de un .zip
  ▼
DwpCreatorViewModel.loadFromZip(uri)
  │  contentResolver.openInputStream(uri) -> ByteArray
  ▼
ZipProjectLoader.load(zipBytes)
  │  1. Descomprime TODAS las entradas del zip a memoria (ZipInputStream, LinkedHashMap<String,ByteArray>)
  │  2. Busca la primera entrada *.dwp
  │  3. DwpDocument.parse(dwpBytes)  ──────────────► DwpTokenizer.tokenize()
  │  4. DwpEngine.listSamples(document) para saber qué .wav espera cada sample
  │  5. Empareja cada sample con su .wav por NOMBRE BASE (sin ruta, sin extensión, case-insensitive)
  │  6. Si falta algún .wav -> ZipLoadException (aborta toda la carga)
  ▼
LoadedProject(document, dwpZipEntryName, audioByIndex, originalZipEntryNames)
  ▼
DwpCreatorViewModel.publishLoaded() -> DwpUiState.Loaded(instrumentName, samples)
  ▼
MainScreen / SampleRow (Compose) -- lista de samples, preview, renombrado global, export

--- Edición ---
DwpCreatorViewModel.renameAll(newName)
  │  DwpEngine.detectInstrumentBaseName(document)   (regex sobre nombres de sample)
  ▼
DwpEngine.renameInstrument(document, oldName, newName)
  │  Recorre TODOS los bloques top-level; para 0x0003 (sample container) recursa;
  │  para cualquier otro bloque, si decodifica como texto imprimible Y contiene oldName,
  │  hace String.replace() y reserializa SOLO ese bloque (recalcula su length).
  │  Todo lo demás se copia byte-a-byte sin tocar.
  ▼
project = project.copy(document = renamedDoc)   (audioByIndex NO cambia -- emparejado por POSICIÓN)

--- Exportación ---
DwpCreatorViewModel.exportTo(uri)
  ▼
ZipProjectExporter.export(project, newFolderName)
  │  Para cada sample (por índice):
  │    1. WavDecoder.decode(audioByIndex[i])         -- valida que sea un WAV real
  │    2. DwpEngine.replaceSampleAudio(doc, i, name, path, wavDecoded)
  │         -> reescribe SOLO el blob 0x01f7 (frameCount/channels/bytesPerSample/sampleRate/bits)
  │            con los valores REALES del wav que se va a escribir
  │  Escribe un nuevo .zip: "<folder>/<folder>.dwp" + "<folder>/<sampleName>.wav" por cada muestra
  ▼
contentResolver.openOutputStream(uri).write(zipBytes)
```

## Diagrama de capas (real, no propuesto)

```
UI (Compose: MainScreen, SampleRow, diálogos)
 ↓ (callbacks)
ViewModel (DwpCreatorViewModel) -- único dueño de estado (StateFlow<DwpUiState>)
 ↓
Domain/IO (ZipProjectLoader, ZipProjectExporter, LoadedProject)  ← puro Kotlin/JVM
 ↓
Domain/DWP (DwpDocument, DwpEngine, DwpTokenizer, DwpBlock, SampleInfo) ← puro Kotlin/JVM
 ↓
Domain/Audio (WavDecoder, PcmConverter) ← puro Kotlin/JVM
```

Capas laterales, invocadas directamente por el ViewModel (no por el pipeline de import/export):
- `audio.SamplePlayer` (Android `AudioTrack`) — solo para preview, nunca toca el `.dwp`.
- `midi.MidiInputManager` (Android `android.media.midi`) — solo dispara `previewSample()`.

`[CODE]` **Esto coincide, en espíritu, con el flujo objetivo del prompt de trabajo** (`UI → Application → Domain → DWP Engine → Parser → Model → Audio → Serializer → Exporter → .dwp`), salvo que aquí no hay una capa "Serializer" separada del propio `DwpTokenizer`/`DwpDocument.toBytes()`, y no existe todavía ninguna capa de "Audio embebido" (porque no se embebe audio).

## Acoplamiento y responsabilidades

`[CODE]` Hallazgos concretos, no genéricos:

- **`ZipProjectExporter` mezcla dos responsabilidades**: (a) reconciliar metadata de audio con `DwpEngine.replaceSampleAudio` y (b) construir el `.zip` de salida. Son cohesivas pero deberían poder probarse por separado si crece la lógica de validación (FASE 10 del plan objetivo).
- **No hay estado global ni singletons abusivos.** `DwpEngine`, `DwpTokenizer`, `WavDecoder`, `PcmConverter` son `object` (stateless, funciones puras) — correcto para lógica de dominio sin necesidad de inyección de dependencias.
- **No hay lógica binaria dentro de la UI.** Los Composables solo reciben `SampleInfo` (DTO ya derivado) y disparan callbacks; el parsing/serialización vive enteramente en `domain/`.
- **Lógica de negocio SÍ vive parcialmente en el ViewModel** (`detectInstrumentBaseName`, orden de llamadas de export) — aceptable a esta escala, pero si el proyecto crece hacia DWP monolítico + FLAC, esa orquestación debería moverse a un caso de uso (`DwpProjectService` o similar) para no sobrecargar el ViewModel. **Se documenta como observación, no como bug.**
- **Bug de integración, mitigado en Fase 1 (`BUG-01`):** originalmente, `MainScreen` definía `onSampleRename`/`onSampleDelete` como parámetros con callback, y `SampleRow` los exponía en un menú contextual, pero `MainActivity.kt` no los pasaba — quedaban en su valor por defecto `{}`. **En Fase 1 se retiraron esas dos opciones del menú** (no se implementaron; se ocultó lo que no funcionaba, ver `16_PHASE1_CHANGES.md`). Estado actual: mitigado, no roto. Implementación real de rename/delete individual sigue pendiente (requiere reindexar `audioByIndex`).
