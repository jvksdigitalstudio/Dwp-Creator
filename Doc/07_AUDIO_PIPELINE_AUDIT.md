# 07 — AUDITORÍA DEL PIPELINE DE AUDIO

## Cobertura de formatos WAV (`WavDecoder`)

`[CODE]`+`[TEST]` Formatos soportados, cada uno con test unitario que construye un WAV sintético válido y verifica la decodificación:
- PCM entero: 8, 16, 24, 32 bits.
- PCM float: 32 bits (`IEEE_FLOAT` tag 3).
- `WAVE_FORMAT_EXTENSIBLE` (tag `0xFFFE`): desenvuelve el sub-formato real leyendo el GUID en el offset `fmtBodyStart + 24`, probado para 24-bit PCM y 32-bit float envueltos.

`[CODE]` Distinción correcta y explícita de conceptos (Sección 17 del prompt de trabajo):
- `bytesPerSample` (1/2/3/4 según `SampleFormat`) ≠ `bitsPerSample` (se deriva, no se confunde).
- `bytesPerFrame = bytesPerSample * channelCount`.
- `frameCount = pcmData.size / bytesPerFrame` — es decir, se calcula, **no** se lee de ningún campo del WAV que pudiera mentir.

`[CODE]` Manejo de chunks RIFF: recorre todos los chunks (no asume orden `fmt` antes de `data`), respeta el padding de alineación a palabra (`size and 1`), y valida explícitamente:
- Tamaño mínimo para leer los magic "RIFF"/"WAVE".
- Presencia de `fmt ` y `data`.
- Que `dataOffset + dataSize` no exceda el tamaño real del archivo (WAV truncado detectado explícitamente, no silenciosamente).

`[CODE]` Endianness: todo el parsing WAV es little-endian, consistente con la especificación RIFF. No se detectó ninguna confusión de signo/unsigned: el único caso unsigned (PCM de 8 bits, convención WAV) se maneja explícitamente en `PcmConverter.uint8ToInt16` centrando en 128.

## Distinción bytes-per-sample vs bits-per-sample vs frames (Sección 17)

`[CODE]` Correctamente separados en todo el código: no se encontró ningún punto donde se usen como sinónimos intercambiables. `DecodedWav.bytesPerSample` se deriva de un `enum SampleFormat`, no de aritmética directa sobre `bitsPerSample` leído del archivo — esto evita que un `bitsPerSample` inconsistente con el `audioFormatTag` cause corrupción silenciosa (de hecho, si la combinación tag+bits no es una de las 5 soportadas, se lanza excepción explícita con el detalle exacto de qué se encontró).

## PCM → destino: dos caminos distintos, verificados por separado

`[CODE]` **Camino 1 — Reproducción (preview):** `WavDecoder.decode` → `PcmConverter.*ToInt16` → `AudioTrack` (siempre PCM16, decisión documentada y justificada: `ENCODING_PCM_FLOAT` no es fiable en todo el hardware Android real). Este camino **nunca** modifica el `.dwp`.

`[CODE]` **Camino 2 — Exportación al `.dwp`:** `WavDecoder.decode` (solo para leer `frameCount`/`channelCount`/`bytesPerSample`/`sampleRateHz`) → `DwpEngine.replaceSampleAudio` reescribe el blob de metadata `0x01f7`. **El PCM decodificado NUNCA se convierte ni se embebe en el `.dwp`** — el `.wav` original (bytes crudos, sin pasar por `PcmConverter`) se escribe tal cual como archivo hermano en el zip de salida. Esto es clave: `PcmConverter` (que sí pierde precisión, ej. trunca 24/32 bits a 16) **no contamina jamás el archivo de audio exportado**, solo se usa para la ruta de escucha en el dispositivo.

## Modelo de audio embebido (monolítico) — estado real

`[CODE]` **No existe ningún camino de código que embeba audio dentro del `.dwp`.** Búsqueda exhaustiva confirma cero referencias a FLAC, a escribir bytes de audio dentro de un bloque DWP, o a ningún tag hipotético `0x0206`. El proyecto es, en su estado actual, **estrictamente "External DWP"**: el `.dwp` solo contiene metadata + referencias de nombre/ruta; el audio real vive siempre en archivos `.wav` separados dentro del mismo `.zip`.

`[INFERENCE]` Esto responde de forma concluyente a la Sección 18 del prompt de trabajo ("¿qué genera realmente el proyecto actual?"): **External DWP**, confirmado por código y por los propios tests de integración (`ZipProjectIoTest`, que siempre empaquetan `.dwp` + `.wav` como archivos separados).

## Memoria (Sección 6.10 / 33 del prompt de trabajo)

`[CODE]` Riesgos de memoria identificados, con evidencia de código exacta:
- `ZipProjectLoader.load` descomprime **el zip completo a memoria** (`entries[entry.name] = zis.readBytes()` dentro de un `while` sin límite de tamaño ni de número de entradas). Para un instrumento de 48 muestras en 32-bit float estéreo a 44.1kHz (~1.5MB por muestra según los frame counts observados: 378.000 frames × 2 canales × 4 bytes ≈ 3MB/muestra), esto son decenas de MB en RAM simultáneamente — aceptable para un instrumento típico, pero **sin ningún techo de seguridad** ante un zip malicioso o corrupto de tamaño extremo (riesgo de OOM, Sección 35).
- `DwpTokenizer.tokenize` tiene un `maxBlocks = 100_000` como única salvaguarda de tamaño — no hay límite de tamaño de payload individual, por lo que un `length` de bloque manipulado (aunque acotado a `payloadStart + length <= n`) podría en teoría solicitar un `copyOfRange` de un payload del tamaño casi total del archivo, sin validación adicional de "tamaño razonable para este tag".
- No se usan streams para el audio en ningún punto del pipeline de exportación/importación — todo son `ByteArray` completos en memoria. Aceptable a la escala actual (instrumentos de decenas de MB), pero **no escala** a bibliotecas de sample grandes (cientos de MB a GB) sin refactorización hacia streaming.

## Concurrencia

`[CODE]` Todo el trabajo pesado (unzip, tokenizar, decodificar WAV, re-zipear) corre en `Dispatchers.Default`/`Dispatchers.IO` dentro de `viewModelScope`, nunca en el hilo principal — correcto. `SamplePlayer` crea un `Thread` dedicado por nota (`daemon = true`) para escribir en el `AudioTrack`, con `synchronized(activeTracks)` para las operaciones de conjunto — correcto para el caso de uso polifónico simple; no hay condiciones de carrera evidentes en el código leído.
