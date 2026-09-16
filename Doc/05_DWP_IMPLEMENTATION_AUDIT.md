# 05 — AUDITORÍA ESPECÍFICA DE LA IMPLEMENTACIÓN DWP

Todo lo listado aquí fue verificado con un **tokenizer Python independiente** (no reutiliza `DwpTokenizer.kt`) ejecutado contra `app/src/test/resources/Instrument.dwp` (47.307 bytes, instrumento cromático de 48 samples, C3–D6/B6, velocidad 127). Cuando se dice `[BINARY]` es porque se comprobó con este script; `[CODE]` es lo que dice el código Kotlin; cuando ambos coinciden se citan juntos.

> **⚠️ CORRECCIÓN POSTERIOR (ver `17_AUDIT_ERRATA.md`, error E-01):** la versión original de este documento afirmaba que el orden de `0x01f4` era `low/root/high`. Tras una segunda verificación exhaustiva sobre las 48 muestras (no solo la muestra 0), se confirmó que el orden real es **`root/low/high`**. La sección "Preámbulo de sample container" y "Bloque `0x01f7`" de más abajo ya reflejan la conclusión corregida; se conserva esta nota para que quede explícito qué cambió y por qué.

## Detección del formato

`[CODE]`+`[BINARY]` Magic de 4 bytes ASCII `"DwPr"` al offset 0. Confirmado: `data[0:4] == b'DwPr'`.

## Preámbulo

`[CODE]`+`[BINARY]` Los primeros **90 bytes (`0x5A`)** son tratados como un bloque opaco fijo, copiado sin interpretar. `DwpDocument.PREAMBLE_SIZE = 0x5a`. No se investigó el contenido interno del preámbulo (qué campos de "configuración global" contiene) porque el código no lo necesita — lo trata como una caja negra que se copia entero. **`[UNKNOWN]`: no hay evidencia sobre el layout interno del preámbulo**, ni sobre si varía entre versiones de DirectWave/FL Studio. El código no contempla variantes de versión — asume un único formato de preámbulo fijo de 90 bytes para todos los `.dwp`.

## Stream de bloques (post-preámbulo)

`[CODE]`+`[BINARY]` Confirmado exactamente el modelo:
```
u32 tag (LE) | u32 length (LE) | u32 reserved (LE, siempre 0 a nivel top) | u8[length] payload
```
- `[BINARY]` Tokenizando desde el offset 90 hasta el final: **160 bloques top-level**, cursor final = 47.307 = tamaño exacto del archivo (0 bytes de sobrante, 0 bytes de déficit).
- `[BINARY]` Los 160 bloques top-level, por tag: `0x0066`×1, `0x0067`×1, `0x0068`×1, `0x0069`×1, `0x006a`×1, `0x006b`×1, `0x006c`×**2**, `0x006d`×**4**, `0x006e`×99, `0x0003`×48, `0x0002`×1.
- `[BINARY]` **Todos los `reserved` top-level son 0** — consistente con lo que documenta `DwpBlock.kt`.

## Tags duplicados (Sección 12 del prompt de trabajo — CRÍTICO)

`[BINARY]` **Confirmado empíricamente que SÍ existen tags duplicados**, tanto a nivel top-level (`0x006c`×2, `0x006d`×4) como dentro de cada contenedor de sample (`0x01fb`×2, `0x0202`×2, `0x0203`×2, y **`0x0204`×16** dentro de un solo sample de 48).

`[CODE]` El modelo `DwpDocument.blocks: List<DwpBlock>` — una **lista**, no un mapa — ya preserva orden y duplicados de forma nativa. **Esto ya cumple el requisito de la Sección 12 del prompt de trabajo; no requiere reingeniería.**

`[CODE]` Sin embargo, `DwpEngine.listSamples()` sí usa `nested.groupBy { it.tag }.firstOrNull()` para extraer campos de **lectura** (nombre, ruta, key range, audio format). Esto es seguro hoy porque los 4 tags que lee (`0x01f4`, `0x01f5`, `0x01f6`, `0x01f7`) aparecen exactamente una vez en cada sample del archivo real — pero **es una asunción no garantizada por el formato**, no por el código. Documentado como riesgo estructural (no bug activo) en `13_RISK_REGISTER.md`.

## Round-trip binary fidelity (Sección 13 del prompt de trabajo)

`[TEST]`+`[BINARY]` Verificado en dos capas independientes:
1. Test `parse then toBytes is a lossless roundtrip on the real file` (JUnit, ya existente en el repo) — compara byte a byte contra el archivo original.
2. Verificación propia de esta auditoría: tokenizando manualmente y re-tokenizando el payload de **cada uno de los 48 sample containers**, el cursor anidado llega exactamente al final de cada payload (`0 mismatches` sobre 48). Esto descarta el riesgo que se había planteado inicialmente por sola lectura de código (que un `tokenize()` anidado pudiera dejar bytes sin cubrir y perderlos al reserializar) — **con este archivo de referencia, no ocurre**. Se deja como `[HYPOTHESIS]` no confirmada la posibilidad de que otro `.dwp` (de otra versión de DirectWave, u otro tamaño de sample) sí produzca un desajuste; el código no verifica explícitamente `nested.endOffset == payload.size` en ninguna de las funciones de `DwpEngine` (`renameRecursive`, `patchFrameCount`, `replaceSampleAudio`, `listSamples`) — solo lo verifica `DwpDocument.parse` a nivel top-level.

## Preámbulo de sample container (tag `0x0003`)

`[BINARY]` Payload de cada `0x0003` es, a su vez, un stream completo de bloques con el mismo formato tag/length/reserved/payload (confirmado: la función de tokenización genérica aplicada recursivamente cubre el 100% de cada payload sin resto). Tags internos observados en el sample 0, con longitudes y repeticiones exactas (verificado también que son las mismas en las 48 muestras salvo donde se indica):

| Tag | Repeticiones | Longitud(es) | Significado (evidencia) |
|---|---|---|---|
| `0x01f4` | 1 | 25 | Key range — **ver sección dedicada justo abajo** |
| `0x01f5` | 1 | 17 | Nombre de sample — `[BINARY]` `"Instrument_C3_127"` |
| `0x01f6` | 1 | 35 | Ruta completa, un solo backslash — `[BINARY]` `"D:\Instrument\Instrument_C3_127.wav"` |
| `0x01f7` | 1 | 40 | Blob de formato de audio — ver detalle abajo |
| `0x01f8` | 1 | 8 | `[BINARY]` hex `0000003f00006400` — ⚫ **UNKNOWN**, sin evidencia de significado |
| `0x01f9` | 1 | 14 | `[BINARY]` todo-cero en el archivo real — ⚫ **UNKNOWN** |
| `0x01fa` | 1 | 48 | `[BINARY]` contiene varios floats no-cero (ej. `0x3f800000`=1.0) — ⚫ **UNKNOWN**, candidato a envelope/tuning por su tamaño, sin confirmar |
| `0x01fb` | **2** | 20 | `[BINARY]` todo-cero — ⚫ **UNKNOWN** (tag duplicado confirmado) |
| `0x01fc` | 1 | 2 | `[BINARY]` todo-cero — ⚫ **UNKNOWN** |
| `0x01fd` | 1 | 16 | `[BINARY]` contiene `0x3f800000` (=1.0) dos veces — ⚫ **UNKNOWN**, candidato a par de ganancias/multiplicadores neutros (1.0), sin confirmar |
| `0x01fe`–`0x0201` | 1 cada uno | 9 | `[BINARY]` todo-cero — ⚫ **UNKNOWN** |
| `0x0202` | **2** | 16 | `[BINARY]` todo-cero — ⚫ **UNKNOWN** (tag duplicado confirmado) |
| `0x0203` | **2** | 20 | `[BINARY]` todo-cero — ⚫ **UNKNOWN** (tag duplicado confirmado) |
| `0x0204` | **16** | 8 | Matriz de modulación — ver sección dedicada abajo |
| `0x0004` | 1 | 0 | Terminador de sample (longitud 0) |

Se reemplaza aquí el rango vago `0x01f8`–`0x0204` de la versión anterior de este documento por la tabla exacta de arriba: **ninguno de estos tags fue decodificado con éxito** más allá de observar sus bytes crudos; se mantiene la política correcta ya seguida por el código de preservarlos verbatim (Sección 24 del prompt maestro).

## Matriz de modulación (`0x0204`) — Sección 23 del prompt maestro

`[BINARY]` Confirmado: **16 bloques separados** con tag `0x0204` (no un único bloque de 128 bytes), cada uno de **exactamente 8 bytes**, en las 48 muestras. Esto coincide estructuralmente, byte a byte, con la hipótesis de 16 "slots" de `u16 source + u16 target + f32 amount` (2+2+4=8). En las 48 muestras, el patrón es idéntico: el slot 0 tiene `source=2, target=2, amount=0.0` y los 15 slots restantes están completamente a cero. 🟡 **PARTIALLY CONFIRMED**: la estructura física encaja con la hipótesis; el significado real de `source=2`/`target=2` (a qué modulador/destino corresponde el índice 2) sigue siendo ⚫ **UNKNOWN** — no hay evidencia para traducir esos índices a nombres funcionales (LFO, envelope, etc.).

## Bloque `0x01f4` (key range) — orden físico corregido, con metodología completa

`[BINARY]` **Orden real: `byte[0]=rootKey, byte[1]=lowKey, byte[2]=highKey`** (la versión original de este documento decía `low/root/high` — error corregido, ver `17_AUDIT_ERRATA.md` E-01).

Metodología: cada una de las 48 muestras tiene un nombre de archivo que codifica su nota propia (`Instrument_C3_127`, `Instrument_C#3_127`, ...), y el instrumento es cromático desde la muestra 0 hasta la 47, por lo que la nota esperada de la muestra `i` es `36 + i` (verificado independientemente: la muestra 0 es "C3" con key=36, la muestra 47 es "B6" con key=83, consistente con 4 octavas completas de 12 semitonos). Comparando esa nota esperada contra los bytes crudos de `0x01f4` en las 48 muestras:

- **`byte[0]` coincide con la nota esperada en 48 de 48 muestras, sin una sola excepción** — incluida la muestra 0 (valor 36) y la muestra 47 (valor 83).
- **`byte[1]` coincide con la nota esperada en 47 de 48 muestras**, fallando únicamente en la muestra 0, donde vale 0 en lugar de 36.
- `byte[2]` coincide con la nota esperada salvo en la muestra 47 (última), donde vale 127 en lugar de 83.

Interpretación: `byte[0]` es la **raíz** (nunca se re-mapea, siempre es la nota real de la muestra — coherente con qué es un "root key"). `byte[1]` es el **límite inferior**, que se extiende a 0 únicamente en la muestra más grave (para cubrir todas las notas MIDI por debajo del rango mapeado — patrón estándar de sampler). `byte[2]` es el **límite superior**, que se extiende a 127 únicamente en la muestra más aguda por la misma razón. Esto es exactamente lo que predice la Sección 15 del prompt maestro nuevo, y descarta la interpretación anterior (donde un valor 0 en la posición 1 se interpretaba como `rootKey=0`, semánticamente absurdo para una muestra nombrada "C3").

`[CODE]` **Consecuencia en el código real**: `DwpEngine.listSamples()` asigna `lowKey = keyRange.getOrNull(0)` y `rootKey = keyRange.getOrNull(1)` — es decir, lee las posiciones correctas del array pero con las etiquetas cambiadas. Esto es `BUG-02` (ver `12_KNOWN_ISSUES.md`), con una consecuencia funcional confirmada, no solo semántica: `DwpCreatorViewModel.kt:167` usa `it.lowKey..it.highKey` para decidir qué muestra suena ante una nota MIDI entrante, así que las notas 0-35 no disparan ningún sample en este instrumento (el rango efectivo de la muestra 0 se calcula como `36..36` en vez de `0..36`).

## Bloque `0x01f7` (formato de audio) — offsets confirmados, con una limitación importante del fixture

`[BINARY]` Interpretando los 40 bytes como 10 × int32 LE, para el sample 0 (2 canales, 32-bit float, 44100 Hz, 378.000 frames):

| Offset | Valor crudo | Interpretación | Confianza |
|---|---|---|---|
| 0 | 378000 | Frame count | 🟢 **CONFIRMED** — `[BINARY]` coincide con el tamaño real de `data` del `.wav` |
| 4 | 0 | Sin identificar | ⚫ **UNKNOWN** |
| 8 | 2 | Canales | 🟢 **CONFIRMED** — `[BINARY]` coincide con `WavDecoder.channelCount` |
| 12 | 4 | Bytes por muestra (candidato) | 🟡 **PARTIALLY CONFIRMED** — ver matización abajo |
| 16 | `1194083328` (bits crudos) | Sample rate como **float32**: `44100.0` | 🟢 **CONFIRMED** — `[BINARY]` `struct.unpack('<f', ...) == 44100.0` exacto, coincidencia estadísticamente imposible por azar |
| 20 | 0 | Sin identificar (candidato a loopMode) | ⚫ **UNKNOWN** |
| 24 | 0 | Sin identificar (candidato a loopStart) | ⚫ **UNKNOWN** |
| 28 | 0 | Sin identificar (candidato a loopEnd) | ⚫ **UNKNOWN** |
| 32 | 0 | Sin identificar | ⚫ **UNKNOWN** |
| 36 | 32 | Bits por muestra | 🟢 **CONFIRMED** — `[BINARY]` coincide con `bytesPerSample*8` real del `.wav` |

**⚠️ Limitación metodológica descubierta en esta revisión (ver `17_AUDIT_ERRATA.md`, E-06):** se verificó que **los 10 campos son exactamente idénticos en las 48 muestras del archivo**, incluido el frame count (378.000 en las 48). Esto significa que este fixture, por construcción, **no tiene ninguna variación interna** que permita confirmar o descartar los candidatos de offsets 4/20/24/28/32 — todos podrían ser "reserved" constante, o podrían ser campos reales que simplemente no varían en este archivo concreto (porque ninguna muestra usa loop, y todas comparten formato). **No se puede avanzar más en estos 5 campos sin un segundo `.dwp` de referencia que contenga al menos una muestra con loop activado y/o un bit-depth distinto.**

`[CODE]` Lo que sí es sólido, para los 4 campos `CONFIRMED`: esto coincide **exactamente** con lo que `DwpEngine.replaceSampleAudio` escribe (mismos offsets, mismas fórmulas, incluyendo `sampleRateHz.toFloat().toRawBits()` para el offset 16). **Evidencia binaria confirma la implementación del código** para esos 4 campos.

**Matización sobre el offset 12 (candidato "bytes por sample"):** a diferencia de frameCount/channels/sampleRate/bits (que se cruzan contra un dato del `.wav` real, independiente del propio `.dwp`), el valor 4 en offset 12 también coincide con el `bytesPerSample` real del `.wav` (float32 = 4 bytes) — es el mismo tipo de evidencia cruzada externa que los otros 4 campos `CONFIRMED`, así que no es una coincidencia arbitraria dentro del archivo. Sin embargo, se mantiene en 🟡 `PARTIALLY CONFIRMED` (no 🟢) porque, a diferencia de los otros 4, **no hay ningún segundo archivo de referencia con un bit-depth distinto** (ej. 16-bit) que permita comprobar que este campo cambia de forma consistente con `bytesPerSample` y no con alguna otra variable que en este archivo particular también valga 4 por coincidencia (ej. algún campo de caché o alineación). Esta es exactamente la cautela que pedía la Sección 17 del prompt maestro nuevo: **no se etiqueta como "bytes per sample" con certeza plena; se etiqueta como candidato fuertemente respaldado pero no verificado de forma cruzada con una segunda variante.**

## Bloques desconocidos

`[BINARY]` Los tags `0x01f8`–`0x0203` (ver tabla exacta arriba, con tamaño y contenido crudo de cada uno) fueron inspeccionados byte a byte, pero **ninguno tiene su significado semántico decodificado** — solo se observaron patrones (ej. `0x3f800000`=1.0 repetido en `0x01fd`, posible candidato a valores neutros de ganancia; varios floats no-cero en `0x01fa`, candidato a envelope/tuning). Estos patrones son `[HYPOTHESIS]`, no `[CONFIRMED]`. Deben tratarse como **OPAQUE** hasta nueva evidencia — el código ya los trata correctamente como opacos (los preserva sin interpretar byte a byte), lo cual es la práctica correcta según la Sección 24 del prompt maestro nuevo (antes Sección 22 del prompt original).

## Lo que el motor NO puede hacer hoy (evidencia por ausencia)

`[CODE]` No existe ninguna función que module la **cantidad** de sample containers (añadir/quitar un `0x0003`), ni que cree un `.dwp` desde cero (siempre parte de uno existente vía `DwpDocument.parse`). El "Eliminar" del menú de `SampleRow` no tiene contraparte en `DwpEngine`.
