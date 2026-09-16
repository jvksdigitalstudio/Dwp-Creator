# 08 — ANÁLISIS: DWP MONOLÍTICO (objetivo final vs. estado actual)

## Veredicto basado en evidencia

`[CODE]` El proyecto **NO genera, en su estado actual, ningún tipo de DWP monolítico ni con audio embebido**. Genera exclusivamente el modelo "External DWP": un `.dwp` de metadata + un `.wav` independiente por sample, empaquetados juntos en un `.zip`. Esto se confirma por:
1. Ausencia total de código de encoding/decoding FLAC o de cualquier códec de audio embebido.
2. `ZipProjectExporter.export` escribe explícitamente cada `.wav` como una entrada de zip separada (`zos.putNextEntry(ZipEntry("$newFolderName/${sample.name}.wav"))`), nunca como payload de un bloque DWP.
3. `DwpEngine.replaceSampleAudio` solo reescribe el blob de 40 bytes `0x01f7` (metadata: frame count, canales, bytes/muestra, sample rate, bits) — nunca escribe un bloque de payload de audio nuevo dentro del documento.

## Lo que SÍ existe y es reutilizable para el objetivo final

`[INFERENCE]`, basado en evidencia `[CODE]`+`[BINARY]` de los documentos 05/06:
- El modelo físico de bloques (`DwpBlock`/`DwpTokenizer`/`DwpDocument`) es genérico y **no necesita cambios** para soportar un futuro bloque de audio embebido: insertar un `0x0206` (hipotético) dentro del payload de un `0x0003` es, mecánicamente, exactamente la misma operación que ya hace `renameRecursive`/`replaceSampleAudio` (tokenizar el payload anidado, añadir/modificar un bloque, reserializar).
- `WavDecoder` ya extrae exactamente los parámetros físicos (`sampleRateHz`, `channelCount`, `bytesPerSample`, `frameCount`, PCM crudo) que cualquier encoder FLAC necesitaría como entrada.
- El pipeline de export ya tiene el punto de inserción correcto: dentro del `for` de `ZipProjectExporter.export`, justo donde hoy se llama `DwpEngine.replaceSampleAudio`, es donde en el futuro se llamaría a un `DwpAudioBuilder`/`FlacEncoder` para producir el bloque `0x0206` embebido en lugar de (o además de) actualizar `0x01f7`.

## Lo que falta construir (brecha real, no genérica)

`[UNKNOWN]`/`[HYPOTHESIS]` — nada de esto tiene evidencia en el código actual, todo deberá venir de investigación externa (reverse engineering adicional, según la división de trabajo ChatGPT/Claude del prompt maestro):
1. Confirmar la existencia y estructura real del tag `0x0206` contra un `.dwp` monolítico real de referencia (hoy **no se dispone de ningún archivo de este tipo** en el proyecto — el único fixture binario es `Instrument.dwp`, que es External DWP).
2. Relación exacta entre `0x0206` y `0x01f7`/`0x0004` en un archivo monolítico real (¿coexisten? ¿se sustituye `0x01f7`? ¿cambia su significado?).
3. Un encoder FLAC completo y correcto (STREAMINFO, frames, subframes, predicción, Rice coding, CRC) — inexistente hoy, cero líneas de código relacionadas.
4. Validadores binarios pre-generación (Sección 50 del prompt de trabajo) — inexistentes hoy.

## Conclusión para la reingeniería

`[INFERENCE]` La brecha entre el estado actual y el objetivo final **no es arquitectónica** (la base de tokenización ya sirve), sino de **conocimiento del formato de audio embebido y de un encoder FLAC**, ambos totalmente ausentes del código y que deben tratarse como una investigación nueva, no como una extensión trivial de lo existente. Este documento evita deliberadamente inventar la estructura de `0x0206`: **no hay evidencia suficiente en este proyecto para proponer bytes concretos**, solo el punto de inserción arquitectónico donde iría.
