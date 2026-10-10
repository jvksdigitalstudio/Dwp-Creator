# 09 — AUDITORÍA DE TESTS

> **⚠️ NOTA DE VIGENCIA (añadida en Core Stabilization — Final Verification):**
> este documento describe el estado de la **Fase 1** (32 tests en 4 archivos) y
> quedó congelado ahí — nunca se actualizó a través de Fase 2, Core
> Stabilization Pass 2 ni Pass 3. Sigue siendo válido como registro histórico
> de ese punto, pero **ya no describe el código actual**. En particular:
> - El estado real hoy es **125 tests en 5 archivos** (`DwpEngineTest.kt` 48,
>   `WavDecoderTest.kt` 28, `MidiMessageParserTest.kt` 29, `ZipProjectIoTest.kt`
>   14, `PcmConverterTest.kt` 6), no los 32/4 descritos abajo. Ver
>   `Doc/18_CANONICAL_STATE.md` §E para el conteo vigente y verificado por
>   `grep -c "@Test"` directo sobre el código.
> - `BUG-02` (etiquetas `lowKey`/`rootKey` intercambiadas), presentado más
>   abajo como una brecha de cobertura **abierta**, fue **corregido en Fase 2**
>   (orden corregido a `[root, low, high]` en `DwpEngine.listSamples()`, con 2
>   tests nuevos que verifican explícitamente la semántica, no solo los
>   valores numéricos). Ver `Doc/13_RISK_REGISTER.md` R-09 (**CERRADO**).
> - `MidiInputManager` figura abajo como "cero cobertura"; esto también
>   cambió: la lógica de parsing se extrajo a `MidiMessageParser` (Kotlin/JVM
>   puro), que sí tiene 29 tests dedicados (`MidiMessageParserTest.kt`).
>
> Para el estado de tests vigente, usar siempre `Doc/18_CANONICAL_STATE.md`,
> no este documento.

`[CODE]` 4 archivos de test, todos JUnit4 puro-JVM (sin Robolectric, sin `androidTest/` instrumentado). **Total: 32 métodos `@Test`** (conteo fresco: `grep -c "@Test"` por archivo + lectura completa, re-verificado en esta pasada). Todos fueron leídos completos, no solo por nombre.

> **⚠️ CORRECCIÓN (ver `17_AUDIT_ERRATA.md`, errores E-03/E-04/E-05):** la versión anterior de este documento decía "26 métodos `@Test`" con `WavDecoderTest.kt`=11 y `PcmConverterTest.kt`=7. Ambas cifras eran incorrectas incluso antes de la Fase 1 (nunca fueron 11/7; fueron siempre 9/6 — error de conteo de la auditoría original, no un cambio posterior). Cifras corregidas abajo.

## `DwpEngineTest.kt` (13 tests: 9 originales de Fase 0 + 4 añadidos en Fase 1) — el más crítico

Corre contra el fixture **real** `Instrument.dwp`, no contra datos sintéticos.

| Test | Qué valida realmente | Qué NO valida |
|---|---|---|
| `real file parses with zero leftover bytes` | Que `DwpDocument.parse` no lance excepción y produzca bloques | No verifica el CONTENIDO de los bloques, solo que el parseo no falle |
| `parse then toBytes is a lossless roundtrip on the real file` | **Round-trip binario exacto** (`bytes.contentEquals(rebuilt)`) — la prueba más fuerte del proyecto | — |
| `finds exactly 48 samples with correct notes and velocities` | Cantidad de samples, nota/velocidad de extremos (índice 0 y 47) | No verifica los 46 samples intermedios individualmente |
| `key range extends to full keyboard at both ends...` | Valores numéricos de `lowKey`/`rootKey`/`highKey` de los extremos y de un sample intermedio | **No verifica que la ETIQUETA asignada a cada offset sea la correcta** — ver `BUG-02` en `12_KNOWN_ISSUES.md`: el test pasa igual aunque `lowKey`/`rootKey` estén con los nombres intercambiados, porque solo compara números, no yo verifica semántica de qué offset es cuál |
| `frame count matches real audio exactly for every sample` | **Todos los 48 samples**, frame count = 378000 | Solo cubre el caso de un instrumento uniforme (todas las muestras del mismo largo) |
| `detectInstrumentBaseName finds Instrument` | Detección de prefijo común vía regex | No prueba con nombres irregulares/no conformes a la regex |
| `renameInstrument replaces name everywhere...` | Rename + reparseo + invariancia de campos binarios (frameCount, rootKey, highKey) | No prueba renombrar a un nombre que colisione con contenido de otros bloques (RISK-01) |
| `renameInstrument works with a shorter or longer new name` | Recalculo correcto de `length` cuando el nombre cambia de tamaño (5 y 18 caracteres vs. 10 original) | — |
| `patchFrameCount updates only the targeted sample` | Que solo el sample objetivo cambie, los demás 47 queden intactos | Esta función (`patchFrameCount`) está **superada** por `replaceSampleAudio` según el propio comentario del código — el test sigue siendo válido pero la función ya no se usa en el flujo de producción (`ZipProjectExporter` usa `replaceSampleAudio`, no `patchFrameCount`) |
| `tokenizeStrict throws instead of silently dropping unconsumed trailing bytes` *(Fase 1)* | Que `DwpTokenizer.tokenizeStrict` lance `DwpFormatException` ante un payload con bytes finales no interpretables | No prueba otros tipos de corrupción (longitud inválida, header a medias) — solo el caso de "bytes sobrantes al final" |
| `renameInstrument refuses to edit a document with a corrupted sample container` *(Fase 1)* | Que `renameRecursive` use la variante estricta y rechace en vez de truncar silenciosamente | — |
| `patchFrameCount refuses to edit a corrupted sample container rather than lose bytes` *(Fase 1)* | Mismo caso que el anterior, para `patchFrameCount` | — |
| `listSamples still reads a corrupted sample container leniently` *(Fase 1)* | Que la función de solo-lectura siga degradándose con gracia (no falle duro) ante el mismo contenedor corrupto | — |

`[TEST]` Todos estos tests **se ejecutan realmente contra bytes binarios reales**, no contra mocks ni contra la intención del código — cumplen el estándar de evidencia `[TEST]` de la Sección 41 del prompt de trabajo original. **Re-verificado en esta pasada (Sección 7 del nuevo prompt maestro): los 4 tests de Fase 1 existen tal cual se documentaron, confirmado por lectura completa del archivo (no solo por lo que decía `16_PHASE1_CHANGES.md`).**

## `WavDecoderTest.kt` (9 tests — corregido, no 11)

Construye WAVs sintéticos **válidos según spec** (no archivos reales) y verifica: PCM 8/16/24/32-bit, float32, `WAVE_FORMAT_EXTENSIBLE` (dos sub-formatos), rechazo de magic RIFF inválido, rechazo de tag de formato no soportado. Cobertura completa de las ramas de `WavDecoder.decode`. **No prueba** archivos WAV reales de terceros (Adobe Audition, etc.) mencionados en los comentarios del propio decoder como motivación — la cobertura es sobre la lógica de parsing, no sobre archivos reales adicionales.

## `PcmConverterTest.kt` (6 tests — corregido, no 7)

Cubre las 4 funciones de conversión (`floatToInt16`, `uint8ToInt16`, `int24ToInt16`, `int32ToInt16`) con casos de rango completo, clamping, y valores medios. Matemáticamente correcto y verificable a mano (valores como `Short.MAX_VALUE` para entrada `1.0f`). **No se usa en el pipeline de exportación** (ver `07_AUDIO_PIPELINE_AUDIT.md`), por lo que estos tests validan un componente que solo afecta la ruta de preview/escucha, nunca el archivo `.dwp` final.

## `ZipProjectIoTest.kt` (4 tests)

Usa el `.dwp` real como base pero genera `.wav` **placeholder sintéticos** (válidos según RIFF, con el nombre del sample codificado como payload PCM, para poder verificar identidad/posición sin necesitar audio real). Cubre: carga con emparejamiento correcto de 48 samples, error claro si falta el `.dwp`, error claro si falta algún `.wav`, y **ciclo completo load→rename→export→reload** verificando que el audio se preserva por posición aunque el nombre cambie. Este último test es la prueba de integración más fuerte del proyecto — ejercita las 4 clases del pipeline principal juntas.

## Clasificación por tipo (Sección 6 del nuevo prompt maestro)

| Test | Fixture real | Datos sintéticos | Tipo |
|---|---|---|---|
| `DwpEngineTest` (13) | ✅ `Instrument.dwp` | — | Integración + regresión binaria |
| `WavDecoderTest` (9) | — | ✅ WAV construido en memoria | Unitario de parsing |
| `PcmConverterTest` (6) | — | ✅ floats/bytes construidos a mano | Unitario matemático |
| `ZipProjectIoTest` (4) | ✅ `Instrument.dwp` (para el `.dwp`) + ⚠️ WAV sintético placeholder (para el audio) | Parcial | Integración de pipeline completo |

**Ningún test documentado está ausente del código, y ningún test del código está sin documentar** — verificado por conteo cruzado exacto (`grep` vs. tabla) en esta pasada.

## Lo que NO está cubierto por ningún test (ausencia confirmada, no asumida)

`[UNKNOWN]` (por ausencia de test, no por fallo):
- `SamplePlayer` (Android `AudioTrack`) — no testeable en JVM puro sin Robolectric/instrumentación; cero cobertura.
- `MidiInputManager` — mismo caso, cero cobertura.
- `DwpCreatorViewModel` — cero tests de ViewModel (no hay `androidx.arch.core:core-testing` ni tests de coroutines en las dependencias).
- Toda la capa `ui/` (Compose) — cero tests de UI, ni siquiera de aserciones de recomposición.
- El caso de un `.dwp` **corrupto/malformado** de otras formas (longitud inválida, header a medias) — los 4 tests de Fase 1 cubren específicamente "bytes sobrantes al final de un payload anidado", pero no longitud negativa tras conversión, ni overflow aritmético, ni header truncado a mitad de campo.
- Ningún test compara bytes/tags/offsets de forma genérica más allá de lo que ya hacen los tests existentes.
- **Ningún test verifica la semántica de las ETIQUETAS de `0x01f4`** (ver `BUG-02`) — los tests existentes pasan con los nombres `lowKey`/`rootKey` intercambiados porque solo comparan valores numéricos contra lo que el propio código (con el bug) produce, no contra una referencia externa independiente.

## Conclusión

`[TEST]` La suite de tests es **pequeña pero de alta calidad real** — no es "test theater": ejecuta lógica real contra un binario real, incluyendo el caso más difícil de verificar (round-trip exacto). Las brechas están en la capa Android (esperable sin Robolectric), en robustez ante corrupción deliberada más allá del caso ya cubierto en Fase 1, y en la ausencia de una verificación semántica independiente de las etiquetas de campo (que es precisamente lo que permitió que `BUG-02` pasara desapercibido en la auditoría anterior).

