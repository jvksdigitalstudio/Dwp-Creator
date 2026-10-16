# 27 — FASE 2: INTEGRACIÓN REAL DE MONOLITHIC DWP EN EL FLUJO DE APLICACIÓN

**Fecha:** 2026-09-20
**Punto de partida:** `Doc/26_PHASE1_FLAC_MONOLITHIC_INTEGRATION_REPORT.md`. Mismo entorno de trabajo (sin `.git`/Gradle/Android SDK/red), documentado y confirmado ahí, sin cambios.

## 1. Auditoría del flujo real (antes de escribir nada)

Se leyó código real, no documentación ni nombres de clase, para reconstruir el flujo de exportación tal como existe hoy:

- **`MainActivity.kt`**: `DwpCreatorApp()` registra dos `rememberLauncherForActivityResult` (`OpenDocument` para importar, `CreateDocument("application/zip")` para exportar) y pasa `onExportClick = { loadedState?.let { exportZipLauncher.launch(...) } }` directo a `MainScreen`, sin ningún paso intermedio.
- **`MainScreen.kt`**: solo recibe callbacks (`onExportClick: () -> Unit`, etc.); no conoce `DwpDocument`, `LoadedProject` ni nada del dominio -- correcto punto de apoyo para añadir una elección de formato sin tocar su contrato.
- **`DwpCreatorViewModel.kt`**: `exportTo(uri)` es la única función de exportación; construye los bytes con `ZipProjectExporter.export(current, loadedState.instrumentName)` y los escribe al `Uri` recibido del picker, con manejo de estado (`isExporting`) y errores (`DwpUiState.Error`) ya establecido.
- **`ZipProjectExporter.kt`**: para cada muestra, decodifica su `.wav` (`WavDecoder.decode`), llama a `DwpEngine.replaceSampleAudio` (Core) para resincronizar `0x01f7`, y escribe un `.dwp` clásico + un `.wav` por muestra dentro de un `.zip`.
- **`LoadedProject.kt`**: el tipo real que conecta ambos flujos -- `document: DwpDocument` + `audioByIndex: List<ByteArray>` (bytes de archivo `.wav`, sin decodificar), emparejados por posición. Es el mismo tipo que ya consume `ZipProjectExporter.export`.
- **`MonolithicDwpBuilder.kt`/`MonolithicDwpAudioBuilder.kt`**: ya auditados en `Doc/24`-`Doc/26`. Punto clave para esta fase: `MonolithicDwpBuilder.build(doc, sampleContainerIndex, wav, ...)` opera **una muestra a la vez** sobre un `DwpDocument` -- no existe, antes de esta fase, ningún punto de entrada que recorra **todas** las muestras de un proyecto real.

**Conclusión de la auditoría (punto correcto de integración):** faltaba exactamente una pieza, al mismo nivel arquitectónico que `ZipProjectExporter` (orquestador de `domain/io/`, hoy sin par para Monolithic) -- una clase que haga con `MonolithicDwpBuilder.build` lo mismo que `ZipProjectExporter.export` ya hace con `DwpEngine.replaceSampleAudio`: recorrer todas las muestras de un `LoadedProject` real, encadenando el documento. No hacía falta, ni se justificó, tocar `DwpEngine`, `ZipProjectExporter`, ni ningún archivo de `domain/flac/` o del Core -- la auditoría no encontró ningún problema en esas capas que bloqueara esta integración.

## 2. Decisión de arquitectura

- **Nueva clase de dominio** `MonolithicDwpProjectBuilder` (`domain/dwp/monolithic/`), simétrica a `ZipProjectExporter` pero para el flujo Monolithic: recibe `LoadedProject`, decodifica cada `.wav` en memoria, y encadena `MonolithicDwpBuilder.build` una vez por muestra. No conoce `.zip` (Monolithic y ZIP siguen siendo conceptos distintos, como documenta el propio `MonolithicDwpBuilder`). Devuelve un `DwpDocument`/`ByteArray` de un único `.dwp` autocontenido -- sin `.wav` externos, porque el audio ya vive embebido.
- **UI**: en vez de convertir el botón EXPORT en dos botones (apretaría una barra ya llena de 4 botones a peso igual), se añadió `ExportFormatDialog` -- mismo patrón de diálogo ya usado por `RenameAllDialog`/`MidiDevicesDialog` -- que el usuario ve al pulsar EXPORT, con las dos opciones explicadas en términos de usuario final (cuántos archivos produce, aviso honesto de que la compatibilidad DirectWave real del `.dwp` autocontenido no está confirmada), sin exponer tags/offsets/FLAC/CRC.
- **ViewModel**: `exportMonolithicTo(uri)`, mismo patrón exacto que `exportTo` (mismo manejo de `isExporting`/errores) para que ambas exportaciones se sientan como la misma acción con un formato distinto, no dos flujos separados de cara al usuario.
- **`MainActivity`**: un segundo `rememberLauncherForActivityResult` (`CreateDocument("application/octet-stream")`, sin mime type oficial de `.dwp`) y el estado del nuevo diálogo, mismo estilo que los diálogos ya existentes.

Nada de esto tocó `DwpEngine`, `ZipProjectExporter`, `DwpBlock`/`DwpDocument`/`DwpTokenizer`, ni ningún archivo de `domain/flac/` -- confirmado por el listado de archivos modificados (§4).

## 3. Cambios realizados

**Nuevos:**
- `app/src/main/java/com/jvk/dwpcreator/domain/dwp/monolithic/MonolithicDwpProjectBuilder.kt`
- `app/src/main/java/com/jvk/dwpcreator/ui/components/ExportFormatDialog.kt`
- `app/src/test/java/com/jvk/dwpcreator/domain/dwp/monolithic/MonolithicDwpProjectBuilderTest.kt` (5 tests)

**Modificados:**
- `app/src/main/java/com/jvk/dwpcreator/viewmodel/DwpCreatorViewModel.kt` -- nueva función `exportMonolithicTo(uri)`.
- `app/src/main/java/com/jvk/dwpcreator/MainActivity.kt` -- segundo launcher, estado del diálogo, `onExportClick` ahora abre el diálogo en vez de exportar directo.

**Eliminados:** NONE.

**Core/FLAC/ZipProjectExporter/DwpEngine:** sin cambios (§1/§2).

## 4. Tests

5 tests nuevos en `MonolithicDwpProjectBuilderTest.kt`, usando el mismo estilo de fixture sintético (documento con forma real de `Instrument.dwp`) y el mismo helper `buildWav` de `WavDecoderTest`, pero operando sobre `LoadedProject` real (3 muestras, mono y stereo mezcladas) en vez de una sola muestra:

- embebe FLAC en las 3 muestras del proyecto (no solo la primera), y cada una decodifica exactamente al PCM correcto (verifica que no se mezclan entre sí).
- `buildBytes` produce los mismos bytes que ensamblar `build()`.
- falla con `IllegalStateException` identificando el **nombre de la muestra concreta** (no un mensaje genérico) cuando un `.wav` en memoria está corrupto.
- falla con `IllegalArgumentException` cuando `audioByIndex.size` no coincide con el número de muestras del documento.
- propaga `UnsupportedMonolithicFormatException` para una muestra `float32` real dentro de un proyecto de varias muestras (no la ignora ni la sustituye en silencio).

**Conteo total del proyecto tras esta fase: 206 métodos `@Test`** (201 tras `Doc/26` + 5 nuevos). Igual que todos los tests de FLAC/Monolithic hasta la fecha, **no ejecutados en Gradle real** en este entorno (sin Gradle/Kotlin/Android SDK/red, confirmado en `Doc/26`) -- verificados por lectura/auditoría del código, siguiendo exactamente los mismos patrones (fixtures, aserciones de contenido real, no solo ausencia de excepción) ya usados y ya ejecutados con éxito en corridas de CI reales para el resto del subsistema.

## 5. Estado de 0x0206 / 0x0205 / 0x01F7

Sin cambios respecto a `Doc/26`: `0x0206` sigue **UNCONFIRMED** contra un `.dwp` real de DirectWave, `0x0205` sigue sin escritor, `0x01F7` sigue **PARTIALLY CONFIRMED**. Esta fase conecta la funcionalidad ya auditada al flujo real de la app; no aporta ni pretende aportar evidencia nueva sobre esos campos -- eso solo lo puede cerrar una referencia real de DirectWave (`REAL_MONOLITHIC_REFERENCE = MISSING`, sin cambios) o una prueba real en FL Studio con el `.dwp` autocontenido que esta fase ya permite generar desde la app.

## 6. Riesgos y limitaciones

- Ídem `Doc/26`: sin corrida de CI real todavía sobre ningún cambio de esta rama.
- El `.dwp` autocontenido nunca se ha probado abriéndolo en DirectWave/FL Studio real -- el diálogo de exportación lo etiqueta honestamente como "experimental" con ese aviso explícito, no como una alternativa equivalente al ZIP.
- `isExporting` (indicador de progreso) existe en el estado desde antes de esta fase pero no se renderiza todavía en ninguna parte de la UI -- gap preexistente, no introducido ni corregido en esta fase (fuera de su alcance).

## 7. Próximo paso recomendado

Push + corrida real de CI cubriendo los 206 tests actuales; después, generar un `.dwp` autocontenido real desde la app (una vez compilada) y probarlo en DirectWave/FL Studio real -- es la única forma de mover `0x0206` de UNCONFIRMED a CONFIRMED o PARTIALLY CONFIRMED con evidencia real.
