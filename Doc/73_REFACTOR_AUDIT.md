# 73 — Auditoría del código tocado por la refactorización modular

> Complementa `Doc/72_MODULAR_ARCHITECTURE_REFACTOR.md`. **Estado: auditoría estática; ni el código ni
> esta auditoría se han compilado ni ejecutado** (entorno sin Kotlin/Gradle/red). El veredicto
> definitivo lo da la primera corrida de CI.

## 1. Método (tres controles independientes)

1. **Auditoría diferencial contra el original intacto.** Cada declaración de nivel superior del árbol
   nuevo se compara, normalizada (sin `package`/imports, sin modificadores de visibilidad en la
   declaración, con los nombres calificados reducidos a simples), con la declaración del mismo nombre
   en el código original sin tocar. Resultado: *idéntica*, *modificada* (con líneas añadidas/quitadas),
   *extraída* (antes anidada) o *nueva*.
2. **Verificador estático de símbolos** (calibrado contra el árbol original que compila: 0 hallazgos;
   probado con mutaciones). Cubre referencias sin resolver, uso de `private`/`internal` ajeno y
   dependencias entre módulos no declaradas.
3. **`tools/check_architecture.py`** (reglas R1–R4 de `Doc/72`), también probado con mutaciones.

## 2. Resultados

| Código | Total | Idéntico al original | Modificado | Extraído (era anidado) | Nuevo |
|---|---:|---:|---:|---:|---:|
| Producción (declaraciones de nivel superior) | 312 | **243** | 32 | 7 | 30 |
| Tests (clases/soportes) | 60 | 26 | 15 | — | 19 |

* **El 77 % de las declaraciones de producción es idéntico al original** línea a línea (solo cambian `package`, imports y, donde se separó un ayudante, `private`→`internal`).
* Controles estáticos finales: **0 hallazgos** de símbolos/visibilidad/módulos, **0 ciclos** entre paquetes,
  **0** imports en conflicto, no usados o redundantes (1 723 revisados), **0** nombres duplicados en un paquete,
  **0** violaciones de R1–R4.
* Tests: 480 originales conservados + 41 nuevos = **521** métodos `@Test`.

## 3. Las 32 declaraciones de producción modificadas (revisadas una a una)

Cada modificación se leyó con su *diff* real. Ninguna altera lógica salvo el ViewModel (reescrito, §4):

| Motivo | Declaraciones |
|---|---|
| **Reapuntar referencias** a tipos que pasaron a nivel superior o a los tres objetos que sustituyen a `DwpEngine` (`WavDecoder.DecodedWav`→`DecodedWav`, `DwpEngine.x`→`DwpSampleCatalog/DwpRenamer/DwpAudioPatcher.x`, `SamplerEngine.VoiceToken`→`VoiceToken`, `DelaySync.effectiveTimeMs`→`DelayTimeResolver.effectiveTimeMs`) | `PcmNormalizer`, `MonolithicDwpAudioBuilder`, `MonolithicDwpBuilder`, `MonolithicDwpProjectBuilder`, `MonolithicDwpStructureBuilder`, `MonolithicDwpValidator`, `LoadedProject`, `ZipProjectExporter`, `ZipProjectLoader`, `SamplerVoice`, `VoiceNote`, `DecodedSampleCache`, `MasterFxChain`, `SamplePlayer` |
| **Solo KDoc/comentarios** | `InstrumentNameValidator`, `SampleInfo`, `SampleOctave`, `SampleMixerState`, `FlacPcmAudio`, `Dsp`, `MixerGestures`/`MixerPanControl` (KDoc devuelto a su función) |
| **Extracción de tipos** (se quitan del contenedor) | `WavDecoder` (−3 tipos), `ZipProjectExporter` (−`PreparedZipExport`), `ZipProjectLoader` (−`ZipLoadException`), `SamplerEngine` (−`VoiceToken`), `DecodedSampleCache` (−`CachedSample`), `DelaySync` (−`effectiveTimeMs`) |
| **Contratos** (`: Interfaz`, `override`; sin cambio de cuerpo) | `MidiInputManager` → `MidiInput`; `SamplePlayer` → `SamplerPlayback` (se quitan los valores por defecto de la implementación, que pasan a la interfaz); `SamplerFxPreferences` → `FxStateStore` |
| **Enganche de inyección** | `DwpCreatorApplication` (+`container`), `MainActivity` (+`viewModels`, pasa el ViewModel), `DwpCreatorApp` (el parámetro ya no tiene valor por defecto `viewModel()`) |
| **Constante unificada** | `PreviewScale.kt` (`MIN_VISIBLE_AMOUNT`, antes duplicada en dos archivos) |
| **Reescrita** | `DwpCreatorViewModel` (706 → 162 líneas, ver §4) |

**Extraídas** (7): `DecodedWav`, `SampleFormat`, `WavFormatException`, `ZipLoadException`,
`PreparedZipExport`, `VoiceToken`, `CachedSample` — cuerpos idénticos a los anidados originales.

**Nuevas** (30 declaraciones): `SamplerPlayback`, `FxStateStore`, `MidiInput`, `SampleZoneResolver`,
`DwpRenamer`/`DwpAudioPatcher`/`DwpSampleCatalog` (división de `DwpEngine`, cuerpos trasladados), `DelayTimeResolver`,
y la capa de sesión/DI: `AppContainer`, `AppDispatchers`, `AppServices`, `ContentResolverFileStreams`, `DwpCreatorViewModelFactory`, `ExportFormat`, `ExportProgressPacer`, `ExportProjectUseCase`, `FileStreams`, `FxController`, `LoadProjectUseCase`, `LoadedSamples`, `MidiNoteRouter`, `MidiSession`, `MixerController`, `MonolithicExportFormat`, `NotePlayer`, `ProjectSession`, `RenameInstrumentUseCase`, `UserFacingErrors`, `ZipExportFormat`, `PreparedExport`.

## 4. El ViewModel: equivalencia de comportamiento

Es el único código con lógica reescrita, por eso se contrastó contra el original punto por punto.
Trasladado **literalmente**: mensajes de error y de progreso (texto idéntico, incluido el prefijo distinto de la
exportación monolítica), etiquetas «Preparando / Empaquetando / Codificando FLAC / Escribiendo archivo», pacing
(900 ms objetivo, 4–40 ms por paso), resaltado de 250 ms, debounce de guardado de 400 ms, búfer de lectura de
64 KiB, regla de Solo/Mute, descarte del estado «por defecto» del mezclador, orden de reinicio al cargar un
instrumento (luces → notas MIDI → mezclador → motor → publicar → *prewarm*), cálculo de la tecla raíz (0..127 o −1),
mapa nota física → índice de muestra para el Note Off, y liberación al destruirse (MIDI → guardar FX → liberar audio).

Diferencias deliberadas (también en `Doc/72` §10):

1. `CancellationException` se relanza en vez de mostrarse como error.
2. La comprobación «¿ocupado?» de `loadFromZip` ocurre dentro de la corrutina (mismo efecto).
3. `AndroidViewModel` → `ViewModel` + fábrica.

Cubierto por pruebas nuevas (41): `MixerControllerTest` (7), `NotePlayerTest` (9), `MidiNoteRouterTest` (6),
`FxControllerTest` (4), `ProjectSessionTest` (7), `ExportProgressPacerTest` (3), `SampleZoneResolverTest` (5).
**No cubierto por pruebas:** `LoadProjectUseCase`, `RenameInstrumentUseCase` y `ExportProjectUseCase` (requieren
`Uri`/flujos reales o `kotlinx-coroutines-test`, no presentes en el proyecto) y el cableado del ViewModel.

## 5. Hallazgos de la propia auditoría

| # | Hallazgo | Origen | Resolución |
|---|---|---|---|
| H1 | Mi primera extracción de `PreparedZipExport` **cortó la clase a mitad** y dejó `writeTo` huérfano dentro de `ZipProjectExporter` | Error mío (corte por la primera cadena `/**` encontrada) | Detectado por el verificador; extracción rehecha con emparejamiento de llaves; el resto de extracciones se revisaron una a una |
| H2 | Ciclo `session` ↔ `session.project` por `AppServices` | Diseño inicial | `AppServices` movido a `di` |
| H3 | Prueba de integración presets→cadena en el módulo equivocado (`:core:sampler-api` no ve el motor) | Estructura previa | Movida a `PresetChainRenderTest` (motor) |
| H4 | `sampleAtY` privado promovido a `internal` sin necesidad | Mi plan de movimiento | Se mantiene `private` en el archivo del overlay |
| H5 | El verificador de símbolos no revisaba **miembros** `internal` usados entre módulos | Limitación de mi herramienta | Análisis específico añadido: solo falsos positivos |
| H6 | KDoc de `MixerPanControl` apilado sobre el de otra función | **Preexistente** | Corregido |
| H7 | `MIN_VISIBLE_AMOUNT` duplicada | **Preexistente** | Unificada |
| H8 | `DelaySync` (modelo) dependía de `StereoDelayEffect` (motor) | **Preexistente** | Separado `DelayTimeResolver` |
| H9 | Un test de 48 casos (`DwpEngineTest`) cubría cinco unidades distintas | **Preexistente** | Dividido por sujeto (los 48 se conservan) |

## 6. Riesgos residuales (por probabilidad)

1. **Gradle sin ejecutar** (media): ver `Doc/72` §13. Son 3 líneas concretas; el fallo sería de configuración, no de lógica.
2. **Errores de compilación que un análisis estático no ve** (media-baja): tipos genéricos, inferencia, SAM, sobrecargas. La lógica trasladada es idéntica y los símbolos están verificados, pero el compilador manda.
3. **Baseline Profile parcial** (cierta, impacto bajo): 86 entradas propias se descartaron (archivos divididos); solo afecta al arranque en frío hasta regenerarlo.
4. **Estabilidad Compose** (baja): si el archivo `compose-stability.conf` no se aplica, las clases de otros módulos se tratan como inestables; con *strong skipping* el efecto es pequeño.

## 7. Qué NO se ha tocado (y sigue pendiente)

La refactorización no añade funcionalidad. Siguen pendientes los huecos funcionales señalados en el análisis
previo (CC de MIDI —sustain, mod wheel, All Notes Off—, canal MIDI y reconexión, capas de velocity y
*round-robin*, servicio en primer plano/audio focus, motor nativo Oboe, persistencia por zona, edición de
zonas, DWP monolítico verificado contra una referencia real). Con esta arquitectura cada uno tiene un sitio
claro donde implementarse (`Doc/72` §14).
