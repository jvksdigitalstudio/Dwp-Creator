# 28 — FASE 2.1: AUDITORÍA FORENSE DE FLOAT32 Y REPRESENTACIÓN DE AUDIO EN DWP

**Fecha:** 2026-09-21
**Metodología:** auditoría, sin modificar nada hasta reunir evidencia. Toda afirmación etiquetada según la jerarquía exigida: `[CODE]`, `[TEST]`, `[BINARY]`, `[BUILD]`, `[DOCUMENTATION]`, `[EXTERNAL]`, `[INFERENCE]`, `[UNKNOWN]`.

## 1. Objetivo

Determinar, con evidencia real y no por asunción, qué representa el audio del DWP real de este proyecto, si es genuinamente Float32 o si esa clasificación es una interpretación incorrecta propagada por la documentación, y qué debe hacer `PcmNormalizer` al respecto.

## 2. Estado inicial

`PcmNormalizer.kt` rechaza explícitamente `PCM_32` y `FLOAT_32`, afirmando en su comentario: *"El único fixture real disponible (Instrument.dwp) es 32-bit float"* — una afirmación categórica que esta fase pone a prueba.

## 3. DWP real analizado

`[BINARY]` Tokenizado con un parser Python independiente (nuevo para esta fase, no reutiliza `DwpTokenizer.kt`) contra `app/src/test/resources/Instrument.dwp` (47.307 bytes, único `.dwp` real en el repositorio). Confirmado: preámbulo 90 bytes, 160 bloques top-level, 48 `TAG_SAMPLE_CONTAINER` (`0x0003`), cursor final = tamaño exacto del archivo (0 bytes de sobrante). Coincide exactamente con lo ya documentado en `Doc/05`.

## 4. Análisis de 0x01F7 (los 40 bytes, byte por byte, en las 48 muestras)

`[BINARY]` **Hallazgo central de esta fase**: los 48 bloques `0x01F7` del fixture son **byte-idénticos entre sí en sus 6 primeros campos** (offsets 0x00–0x24); solo cambia lo que vive fuera de este análisis (nombre/ruta/key range en otros tags). Valores observados:

| Offset | Valor (todas las 48 muestras) | Interpretación actual |
|---|---|---|
| +00 | `378000` | frameCount candidato |
| +04 | `0` | sin identificar |
| +08 | `2` | channels candidato |
| +0C | `4` | bytesPerSample candidato |
| +10 | `44100.0` (float32 exacto) | sampleRate candidato |
| +14..+20 | `0` | sin identificar |
| +24 | `32` | bitsPerSample candidato |

## 5. Tabla de campos, clasificación honesta

| Campo | Clasificación | Por qué |
|---|---|---|
| +00 frameCount | 🟡 PARTIALLY CONFIRMED | `[BINARY]` `378000` en las 48 muestras, sin variación entre C3 y B6 (4 octavas) -- inusual para audio grabado real, consistente con muestras generadas/normalizadas a longitud fija, o con un fixture creado como plantilla de prueba. No hay `.wav` real para contrastarlo directamente en esta sesión. |
| +04 | ⚫ UNKNOWN | Sin cambios, siempre 0 en el fixture. |
| +08 channels | 🟢 CONFIRMED (para este fixture) | `[BINARY]` consistente en las 48 muestras; ya confirmado por `Doc/05` contra el `.wav` real que existía en su momento. |
| +0C bytesPerSample | 🟡 PARTIALLY CONFIRMED | Igual que antes (`Doc/05`) -- **pero ver §14: este campo NO distingue PCM32int de Float32, ambos ocupan 4 bytes.** |
| +10 sampleRate | 🟢 CONFIRMED | `44100.0` como float32 exacto, coincidencia estadísticamente imposible por azar. |
| +24 bitsPerSample | 🟡 PARTIALLY CONFIRMED | Igual razón que +0C: `32` es compatible con PCM32int **y** con Float32; el campo no lleva un flag de "es float". |

**Ningún campo de `0x01F7`, en ninguno de sus 10 valores de 4 bytes, codifica explícitamente si la representación es entera o de punto flotante.** Esto es la base del hallazgo principal de esta fase (§14).

## 6. Comparación de las 48 muestras

`[BINARY]` Total uniformidad: mismos channels/sampleRate/bytesPerSample/bitsPerSample/frameCount en las 48. No hay ninguna muestra que difiera. `[INFERENCE]` Esto es compatible con dos escenarios distintos, y la evidencia disponible no permite elegir entre ellos: (a) el instrumento real fue exportado con todas sus muestras normalizadas a una duración fija, práctica real en algunos sample packs comerciales; o (b) este fixture concreto fue generado/recortado específicamente como material de prueba estructural (no como grabación real), y su "audio" -- que no existe en este repo -- pudo nunca haber sido una grabación real en absoluto. `[UNKNOWN]` cuál de las dos es la correcta.

## 7. Análisis de WAV real asociado

`[BINARY]` **No existe ningún archivo `.wav` en este repositorio.** `find . -iname "*.wav"` no devuelve resultados. Confirma lo ya documentado en `Doc/24`. El comentario de `ZipProjectLoader.kt` que menciona "~144MB total uncompressed" para el fixture de 48 muestras es consistente matemáticamente con `378000 frames × 2 canales × 4 bytes × 48 muestras ≈ 145 MB` -- **coincidencia numérica exacta** que confirma que ese comentario describe precisamente el audio de este mismo fixture, aunque ese audio nunca se incluyó en el repositorio (impracticable para un fixture de test versionado en git).

## 8. Auditoría de WavDecoder — el hallazgo más importante de esta fase

`[CODE]` El comentario de cabecera de `WavDecoder.kt` (código de producción, no documentación derivada) dice textualmente:

> *"Originally supported only 16-bit integer PCM and 32-bit IEEE-float PCM -- the two formats seen in the one reference file (FL Studio Desktop's own DirectWave export) used during the original binary audit."*

Esto es una afirmación **directa del propio código fuente**, escrita en una fase muy anterior de este mismo proyecto (antes de que existiera `Doc/05`/`Doc/07`/Monolithic DWP), de que **sí se examinó un `.wav` real** (una exportación real de FL Studio Desktop/DirectWave) durante "the original binary audit" -- y que ese archivo era, en efecto, IEEE float de 32 bits. Este archivo **ya no existe en el repositorio actual** y no es re-verificable en esta sesión.

`[CODE]` `WavDecoder.decode` distingue PCM entero de IEEE float mediante el campo `audioFormatTag` del chunk `fmt ` (offset 0 del cuerpo del chunk, 2 bytes): `FORMAT_PCM = 1` vs `FORMAT_IEEE_FLOAT = 3`, con `WAVE_FORMAT_EXTENSIBLE` (`0xFFFE`) desenvuelto leyendo el GUID de subformato en `fmtBodyStart + 24`. Esta distinción **solo existe en el propio `.wav`** -- el `.dwp` (concretamente `0x01F7`) no la lleva.

`bitsPerSample` se deriva de `SampleFormat` (enum), nunca de aritmética directa sobre el campo `bitsPerSample` crudo del WAV -- así que una combinación `audioFormatTag`+`bitsPerSample` no soportada lanza excepción explícita en vez de producir un `SampleFormat` incorrecto. `frameCount` se calcula (`pcmData.size / bytesPerFrame`), nunca se lee de un campo del WAV que pudiera mentir.

## 9. Auditoría de PcmConverter

`[CODE]`+`[TEST]` (5 tests nuevos de esta fase) `floatToInt16` ya clampaba correctamente valores fuera de `[-1,1]` (incluidas `+Infinity`/`-Infinity`, mediante `coerceIn`). Comportamiento verificado y ahora documentado explícitamente para los casos no cubiertos previamente por ningún test (Sección 22 del prompt de esta fase):
- **NaN**: `coerceIn` no lo recorta (ninguna comparación IEEE 754 con NaN es verdadera), y `(Float.NaN).toInt() == 0` por especificación de Kotlin/Java -- termina en silencio digital (0), no en crash ni en basura.
- **-0.0f**: pasa sin cambios por `coerceIn`, `(-0.0f).toInt() == 0` -- mismo resultado que `0.0f`.
- **+Infinity/-Infinity**: correctamente clampados al mismo máximo/mínimo que cualquier otro valor fuera de rango.

Este es el único punto de todo el pipeline que procesa floats crudos byte a byte (camino de previsualización, nunca escribe al `.dwp`) -- comportamiento ya correcto, ahora con tests de regresión (`PcmConverterTest`, 5 nuevos) y comentario explicativo.

## 10. Auditoría de PcmNormalizer

`[CODE]` Rechaza `PCM_32` y `FLOAT_32` con `UnsupportedMonolithicFormatException` antes de intentar `readSample()` (que además tiene un `error()` de defensa en profundidad para esos dos casos, inalcanzable en la práctica pero correcto). **Esta fase no cambia ese comportamiento** -- sigue siendo la decisión correcta, por una razón más precisa que antes (§14).

`[CODE]` Corregido en esta fase: el KDoc de la clase y el mensaje de la excepción `FLOAT_32` afirmaban como hecho asentado *"el fixture real es 32-bit float"*. Se corrigió para reflejar la cadena de evidencia real (histórica pero no re-verificable, ver §8) en vez de presentarla como una observación directa de esta sesión.

## 11. Auditoría de FlacEncoder (sin tocarlo)

`[CODE]` Confirmado (ya auditado en `Doc/26`, re-verificado aquí): `FlacEncoder`/`FlacPcmAudio` trabajan exclusivamente con `IntArray` -- **PCM entero con signo**, nunca `FloatArray`. "32-bit" en `FlacEncoder` significa siempre PCM entero de 32 bits. **FLAC, como códec, no tiene ningún modo de codificar IEEE float directamente** -- esto es consistente con la especificación pública de FLAC, no una limitación de este proyecto. Por tanto, si alguna vez se decide soportar audio Float32 en el pipeline Monolithic, la única vía posible es `Float32 → PCM entero` antes de llegar a `FlacEncoder` -- nunca al revés, y nunca reinterpretando los mismos 4 bytes como un entero (eso sería el error explícito que la Sección 10 del prompt de esta fase prohíbe).

## 12. Auditoría de 0x0206 (sin tocarlo)

`[CODE]` Sin cambios respecto a `Doc/26`/`Doc/27`. Estructura, `flacLength`, campo reservado, relación con `0x0004`: sin cambios. Estado sin cambios: **IMPLEMENTATION / INTERNALLY CONSISTENT / EXTERNAL COMPATIBILITY UNCONFIRMED**.

## 13. Auditoría de 0x0205

`[CODE]`+`[BINARY]` Sin cambios: no aparece ningún escritor de `0x0205` en el código; el fixture real tampoco contiene ningún bloque con ese tag (confirmado por el conteo de tags de §3: no figura entre los 160 bloques top-level ni se generó ninguno anidado con ese tag). Sigue **UNKNOWN**, sin inventar su estructura.

## 14. Frame count y la pregunta central

`[BINARY]`+`[INFERENCE]` `frameCount(378000) × channels(2) × bytesPerSample(4) = 3.024.000 bytes/muestra`, `× 48 muestras ≈ 145 MB` -- coincide exactamente con el comentario histórico de `ZipProjectLoader.kt` (§7). Esto confirma que la aritmética de `0x01F7` es internamente consistente y que `bytesPerSample=4` efectivamente se usó para calcular ese tamaño total en algún momento -- **pero "4 bytes por muestra" es matemáticamente idéntico para PCM32 entero y para Float32**. Ninguna cantidad de aritmética sobre `0x01F7` puede resolver esta ambigüedad: **se necesitaría el `.wav` real (su campo `audioFormatTag`) para saberlo con certeza directa, y ese archivo no existe en este repositorio.**

## 15. Evidencia — resumen por capas

1. `[CODE]` `WavDecoder.kt` (comentario de cabecera, fase anterior del proyecto): un `.wav` real de FL Studio Desktop/DirectWave fue examinado y era IEEE float 32-bit. **La evidencia más fuerte disponible, pero no re-verificable hoy.**
2. `[BINARY]` (esta fase) `0x01F7` de las 48 muestras: 4 bytes/muestra, 32 bits/muestra -- compatible con, pero no exclusivo de, Float32.
3. `[DOCUMENTATION]` `Doc/05`/`Doc/07`: repitieron la clasificación "float32" citando únicamente la evidencia de `0x01F7` (#2), sin distinguirla explícitamente de la evidencia más fuerte de `WavDecoder.kt` (#1) -- las mezclaron sin etiquetarlas por separado, lo cual sobrestimaba la certeza real alcanzable hoy.
4. `[TEST]` Ningún test de este proyecto (incluido Monolithic DWP) ejercita el pipeline con un `.wav` real que coincida con lo que declara `Instrument.dwp`; `ZipProjectIoTest` usa PCM16 mono sintético como placeholder, deliberadamente sin relación con el formato real del fixture.

## 16. Conclusión

**RESULTADO E**: el formato real requiere una representación (Float32 casi con toda probabilidad, por la evidencia histórica de `WavDecoder.kt`) pero **no disponemos de evidencia re-verificable en este momento** para tratarlo como un hecho confirmado con la confianza que la documentación anterior le atribuía. No es RESULTADO B (no hay evidencia de que sea un error de interpretación) ni RESULTADO A (no "sabemos exactamente" cómo convertirlo -- sabemos *que probablemente* es Float32, pero no qué conversión a PCM entero correspondería al formato que DirectWave esperaría embebido, que sigue siendo 🔴 NO CONFIRMADO en `Doc/23`).

## 17. Decisión técnica

**No implementar ninguna conversión Float32 → PCM entero en esta fase.** La auditoría no produjo la evidencia que la Sección 20 del prompt exige para justificarla (qué ancho de bits de destino, qué escala, qué DirectWave esperaría). `PcmNormalizer` sigue rechazando `FLOAT_32`/`PCM_32` explícitamente -- comportamiento ya correcto, ahora respaldado por una razón más precisa. El único cambio de código de esta fase es documentación (comentarios/mensajes de excepción corregidos para no sobreafirmar) y tests que documentan comportamiento ya-correcto de `PcmConverter` (Sección 22 del prompt).

## 18. Cambios realizados

- `PcmNormalizer.kt`: KDoc de la clase y mensaje de `FLOAT_32` corregidos (sin cambio de comportamiento).
- `PcmConverter.kt`: comentario explicativo del comportamiento NaN/Infinity/-0.0 en `floatToInt16` (sin cambio de comportamiento).
- `PcmConverterTest.kt`: 5 tests nuevos (NaN, +Infinity, -Infinity, -0.0, mezcla de los cuatro en un buffer).

**Sin cambios en:** `WavDecoder`, `MonolithicDwpBuilder`, `MonolithicDwpAudioBuilder`, `DwpBinaryAssembler`, `FlacEncoder`, `FlacDecoder`, `DwpCreatorViewModel`, UI, Core (`DwpBlock`/`DwpDocument`/`DwpTokenizer`/`DwpEngine`/`DwpVersionProfile`).

## 19. Tests

**Escritos:** 5 nuevos en `PcmConverterTest.kt`. **Total del proyecto: 211** (206 tras `Doc/27` + 5). **Ejecutados:** NOT EXECUTED (mismo entorno sin Gradle/Kotlin/Android SDK/red documentado desde `Doc/26`). **No ejecutados:** los 211, por la misma razón ambiental, no por elección.

## 20. Build

    testDebugUnitTest: NOT EXECUTED (sin Gradle real disponible)
    assembleDebug:     NOT EXECUTED (sin Android SDK disponible)

## 21. Compatibilidad externa

Sin cambios: `0x0206` sigue UNCONFIRMED contra DirectWave real. Esta fase no aporta ni pretende aportar evidencia sobre eso -- es una auditoría de representación de audio, no de formato de contenedor.

## 22. Limitaciones

- El `.wav` real que `WavDecoder.kt` menciona haber examinado no está disponible en este repositorio ni en esta sesión -- es la limitación estructural central de toda esta fase.
- `0x01F7` no puede, por diseño (según la evidencia disponible), distinguir PCM32 entero de Float32 -- esto no es una limitación de esta auditoría sino, aparentemente, del propio formato DWP tal como está documentado hasta ahora.
- La uniformidad total de las 48 muestras del fixture (frameCount idéntico) deja abierta la duda de si el fixture representa audio real grabado o material sintético/plantilla -- sin el `.wav`, no se puede resolver.

## 23. Próximos pasos

Si en algún momento se consigue acceso a un `.wav` real que acompañe a `Instrument.dwp` (o a cualquier otro `.dwp` real con su audio), el primer paso debería ser, exactamente, leer su `audioFormatTag` con `WavDecoder` y comparar contra lo aquí documentado -- eso, y solo eso, puede mover esta clasificación de 🟡 PARTIALLY CONFIRMED a 🟢 CONFIRMED o corregirla. Hasta entonces, cualquier decisión de soportar Float32 en el pipeline Monolithic seguiría siendo una decisión sin evidencia suficiente, y por tanto fuera del alcance de lo que este proyecto puede implementar responsablemente hoy.

---

## ADENDA — Cierre formal de la fase (Secciones 22, 32, 33, 34, 35 del prompt maestro)

Esta adenda completa tres entregables formales que la primera versión de este documento resolvió en prosa dentro de las secciones anteriores, pero no en el formato explícito que el prompt de esta fase pedía. No cambia ninguna conclusión ni ningún archivo de código adicional -- es documentación de cierre.

### A. Sección 22 del prompt — lista completa de tests, justificados ítem por ítem

El prompt pedía una lista de 26 categorías de test. Se agrupan aquí con su estado real y la razón exacta:

| # | Categoría pedida | Estado | Razón |
|---|---|---|---|
| 1 | Float32 válido | ⚫ NO ESCRITO (justificado) | No existe ningún conversor Float32→algo en el pipeline Monolithic ni se implementó ninguno en esta fase (§17: sin evidencia para justificarlo). No hay comportamiento que probar. |
| 2 | Float32 mono | ⚫ NO ESCRITO (justificado) | Misma razón que #1. |
| 3 | Float32 stereo | ⚫ NO ESCRITO (justificado) | Misma razón que #1. |
| 4 | valores 0 | 🟢 CUBIERTO (parcial) | `PcmConverterTest` ya cubría `0.0f` antes de esta fase (`converts full-scale values correctly` incluye el caso 0 implícito en el rango probado; ver también el test nuevo de `-0.0f`, #10). |
| 5 | valores ±1 | 🟢 CUBIERTO (ya existía) | `converts full-scale values correctly`, preexistente, prueba exactamente `+1.0f`/`-1.0f`. |
| 6 | valores intermedios | 🟢 CUBIERTO (ya existía) | `half-scale value maps to roughly half of int16 range`, preexistente. |
| 7 | valores >1 | 🟢 CUBIERTO (ya existía) | `clamps out-of-range values instead of overflowing`, preexistente. |
| 8 | valores <-1 | 🟢 CUBIERTO (ya existía) | Mismo test que #7, incluye el lado negativo. |
| 9 | NaN | 🟢 **NUEVO en esta fase** | `NaN sample becomes digital silence, not a crash or garbage value`. |
| 10 | +Infinity | 🟢 **NUEVO en esta fase** | `positive infinity clamps to the same maximum as any other out-of-range value`. |
| 11 | -Infinity | 🟢 **NUEVO en esta fase** | `negative infinity clamps to the same minimum as any other out-of-range value`. |
| 12 | -0.0 | 🟢 **NUEVO en esta fase** | `negative zero converts to silence, same as positive zero`. |
| 13 | frame count | ⚫ NO ESCRITO (justificado) | Sin conversión Float32 que produzca frames nuevos que probar; el frame count de `0x01F7` ya está cubierto por los tests de Monolithic existentes (`MonolithicDwpBuilderTest`/`MonolithicDwpProjectBuilderTest`), sin relación con Float32. |
| 14 | sample rate | ⚫ NO ESCRITO (justificado) | Misma razón que #13. |
| 15 | channels | ⚫ NO ESCRITO (justificado) | Misma razón que #13. |
| 16 | bit depth | ⚫ NO ESCRITO (justificado) | Misma razón que #13. |
| 17 | conversión | ⚫ NO ESCRITO (justificado) | No existe conversión Float32→PCM que probar (§17). |
| 18 | FLAC encode/decode | ⚫ NO APLICA | `FlacEncoder` nunca recibe Float32 (§11: solo `IntArray`); no hay nada de Float32 que codificar en FLAC. |
| 19 | monolithic DWP generation | ⚫ NO APLICA | Misma razón que #18; `PcmNormalizer` sigue rechazando Float32 antes de llegar ahí. |
| 20 | DWP reparse | ⚫ NO APLICA | Sin generación nueva que reparsear. |
| 21 | 0x0206 | ⚫ NO APLICA | Sin cambios en 0x0206 en esta fase (§12). |
| 22 | audio preservation | ⚫ NO APLICA | Sin conversión que preserve o pierda datos. |
| 23 | regression PCM8 | 🟢 CUBIERTO (ya existía) | `PcmNormalizerTest`, sin cambios en esta fase, sigue verde por auditoría de código (no ejecutado, ver §19/§20). |
| 24 | regression PCM16 | 🟢 CUBIERTO (ya existía) | Igual que #23. |
| 25 | regression PCM24 | 🟢 CUBIERTO (ya existía) | Igual que #23. |
| 26 | regression PCM32/export ZIP/invalid input | 🟢 CUBIERTO (ya existía) | `PcmNormalizerTest` (PCM_32 rechazado explícitamente) y `ZipProjectIoTest`/`ZipProjectExporter`-relacionados, sin cambios en esta fase. |

**Resumen:** de las 26 categorías pedidas, 4 son tests genuinamente nuevos de esta fase (#9-12, exactamente los que la ausencia de cobertura real justificaba), 10 ya estaban cubiertas antes de esta fase sin necesidad de cambios, y 12 no aplican porque implementarlas habría exigido escribir precisamente la conversión Float32→PCM que la Sección 20/21 del prompt prohíbe hacer sin evidencia -- escribir esos tests habría sido simular una funcionalidad que no existe y que esta fase, correctamente, no creó.

### B. Sección 32 del prompt — matriz de problemas

| ID | Archivo | Clase | Método | Problema | Evidencia | Impacto | Severidad | Solución propuesta | Estado |
|---|---|---|---|---|---|---|---|---|---|
| P-28-01 | `PcmNormalizer.kt` | `PcmNormalizer` | KDoc de clase + rama `FLOAT_32` de `normalize()` | Afirmaba "el fixture real es 32-bit float" como hecho confirmado de esta sesión, cuando la evidencia real es histórica y no re-verificable | `[DOCUMENTATION]` comentario original; `[BINARY]` 0x01F7 no distingue int/float; `[CODE]` `WavDecoder.kt` es la fuente real, no re-verificable | Riesgo de que un futuro mantenedor tome una decisión de conversión basándose en una certeza que no existe | MEDIUM | Corregir el comentario y el mensaje de excepción para reflejar la cadena de evidencia real, sin cambiar el comportamiento | 🟢 RESUELTO (esta fase) |
| P-28-02 | `Doc/05_DWP_IMPLEMENTATION_AUDIT.md`, `Doc/07_AUDIO_PIPELINE_AUDIT.md`, `Doc/23_MONOLITHIC_DWP_FLAC_IMPLEMENTATION_SPEC.md` | — | — | Mismo sobreclaim que P-28-01, propagado a 3 documentos históricos | `[DOCUMENTATION]` grep directo de "float32"/"32-bit float" en los 3 archivos | Documentación desalineada con la evidencia real disponible hoy | LOW | Nota de corrección al inicio de cada documento, sin borrar el contenido histórico (Sección 27 del prompt) | 🟢 RESUELTO (esta fase) |
| P-28-03 | `PcmConverter.kt` | (object) | `floatToInt16` | Comportamiento ante NaN/+Inf/-Inf/-0.0 correcto pero no documentado ni cubierto por test, pese a ser código de producción del camino de previsualización | `[CODE]` lectura directa de `coerceIn`/`toInt()`/`toShort()`; `[TEST]` ausencia confirmada por `grep` sobre `PcmConverterTest.kt` antes de esta fase | Ninguno funcional (el comportamiento ya era correcto); riesgo de que una futura refactorización lo rompiera sin que ningún test lo detectara | LOW | Documentar el comportamiento exacto en comentario + 5 tests de regresión | 🟢 RESUELTO (esta fase) |
| P-28-04 | `app/src/test/resources/Instrument.dwp` | — | — | Las 48 muestras declaran `frameCount` (+00) idéntico (378000), inusual para audio grabado real de un instrumento de 4 octavas | `[BINARY]` tabla completa de 48 filas, esta fase | Ninguno funcional; incertidumbre sobre si el fixture representa audio real o material sintético/plantilla | INFO | Ninguna -- no hay forma de resolverlo sin el `.wav` real; documentado como limitación | ⚫ ABIERTO (sin solución posible con la evidencia disponible) |
| P-28-05 | (ninguno de código) | — | — | No existe ningún `.wav` real en el repositorio que permita re-verificar `audioFormatTag` directamente | `[BINARY]` `find . -iname "*.wav"` sin resultados | Bloquea confirmar/refutar Float32 con certeza `[BINARY]` propia de esta sesión | INFO | Conseguir un `.wav` real de referencia (recomendación, `Doc/28` §23) | ⚫ ABIERTO (fuera del alcance de esta fase) |

### C. Sección 33 del prompt — matriz de componentes

| Componente | Clasificación | Evidencia |
|---|---|---|
| `WavDecoder` | KEEP | `[CODE]` auditado íntegro en esta fase; distinción PCM/float correcta y ya testeada; su comentario de cabecera es, además, la evidencia histórica clave de esta fase. |
| `PcmConverter` | KEEP (con adenda) | `[CODE]`+`[TEST]` comportamiento ya correcto ante entradas no finitas; solo le faltaba documentación/tests, ya añadidos en esta fase. |
| `PcmNormalizer` | KEEP (con corrección documental) | `[CODE]` decisión de rechazar `PCM_32`/`FLOAT_32` sigue siendo correcta; solo se corrigió el nivel de certeza expresado en comentarios/mensajes. |
| `FlacEncoder`/`FlacDecoder` | KEEP | `[CODE]` re-auditado, sin cambios; confirmado que solo maneja PCM entero, nunca float -- por diseño del propio formato FLAC, no una limitación del proyecto. |
| `MonolithicDwpBuilder`/`MonolithicDwpAudioBuilder`/`DwpBinaryAssembler` | KEEP | Sin cambios; la auditoría de esta fase no encontró ningún problema que los involucrara. |
| `0x01F7` (interpretación de campos) | UNKNOWN (parcial) | `[BINARY]` los campos +00/+0C/+24 siguen sin poder distinguir, por diseño del propio formato hasta donde hay evidencia, PCM entero de IEEE float; no es competencia de ningún componente de código corregirlo. |
| `Instrument.dwp` (fixture) | KEEP | No se modificó ni había razón para hacerlo; es el objeto de estudio, no un componente de la aplicación. |
| Conversión Float32→PCM entero para Monolithic | PLACEHOLDER (inexistente, deliberadamente) | `[INFERENCE]` justificado en §17: no existe evidencia suficiente para diseñarla; implementarla ahora sería inventar (Sección 3/10/20/21 del prompt). Permanece sin implementar hasta que exista esa evidencia. |

### D. Informe final en el formato exacto de la Sección 34

**RESUMEN EJECUTIVO**
La afirmación "el fixture real es Float32" tenía una base real pero más débil de lo que la documentación anterior transmitía: depende de un `.wav` ya no disponible, no de algo que el `.dwp` en sí codifique. Se corrigió la documentación para reflejar esto con precisión; no se cambió ningún comportamiento de código funcional, porque no había evidencia suficiente para implementar una conversión sin inventarla.

**ESTADO REAL DEL PIPELINE**
Sin cambios funcionales: `WavDecoder` sigue distinguiendo PCM/float correctamente desde el `.wav`; `PcmNormalizer` sigue rechazando `PCM_32`/`FLOAT_32` explícitamente antes de llegar a FLAC; `FlacEncoder` sigue aceptando solo PCM entero.

**FLUJO REAL**
`ZipProjectLoader` → `LoadedProject` (bytes de `.wav` crudos) → (en Monolithic) `WavDecoder.decode` → `PcmNormalizer.normalize` (rechaza aquí si es Float32/PCM32) → `FlacEncoder` (solo si pasó la normalización) → `MonolithicDwpAudioBuilder` → `0x0206`. Confirmado por lectura de código, sin cambios respecto a `Doc/27`.

**DWP 0x01F7**
48/48 muestras idénticas en +00/+08/+0C/+10/+24 (378000 frames, 2ch, 4 bytes, 44100Hz, 32 bits). Ningún campo distingue PCM entero de IEEE float.

**WAV**
No existe ningún `.wav` real en este repositorio. La única evidencia de un `.wav` real examinado alguna vez es el comentario histórico de `WavDecoder.kt`.

**PCM**
`PcmConverter.floatToInt16` (único punto que procesa floats crudos) verificado y documentado correcto ante NaN/±Infinity/-0.0; sin cambios de comportamiento, solo comentarios y tests.

**FLOAT32**
Clasificación de `Instrument.dwp` como Float32: 🟡 PARTIALLY CONFIRMED (bajada desde una presentación anterior que sonaba a 🟢 CONFIRMED sin serlo). Ver §15-16.

**FLAC**
Sin cambios; confirmado (de nuevo) que solo acepta PCM entero, nunca IEEE float, por diseño del formato.

**0x0206**
Sin cambios; sigue IMPLEMENTATION / INTERNALLY CONSISTENT / EXTERNAL COMPATIBILITY UNCONFIRMED.

**FRAME COUNTS**
378000 × 2 × 4 × 48 ≈ 145MB, coincide exactamente con el comentario histórico de `ZipProjectLoader.kt` sobre el tamaño del fixture -- confirma la aritmética interna, no la naturaleza int/float de los datos.

**AUDIO REPRESENTATION**
Sin resolver con certeza total; ver conclusión (§16) y matriz de componentes (arriba).

**PROBLEMAS ENCONTRADOS**
5 (matriz B arriba): 3 resueltos en esta fase (documentación/tests), 2 abiertos sin solución posible con la evidencia disponible.

**CAMBIOS REALIZADOS**
Ver §18 del cuerpo principal de este documento.

**TESTS**
211 detectados (206 + 5 nuevos); 0 ejecutados en Gradle real (mismo entorno de siempre).

**BUILD**
`testDebugUnitTest`: NOT EXECUTED. `assembleDebug`: NOT EXECUTED. Razón: sin Gradle/Android SDK/red en este contenedor (sin cambios respecto a fases anteriores).

**DOCUMENTACIÓN**
Nuevo: `Doc/28` (este documento, con esta adenda). Corregidos con nota, sin borrar: `Doc/05`, `Doc/07`, `Doc/23`. Actualizados: `Doc/18`, `README.md`.

**COMPATIBILIDAD EXTERNA**
Sin cambios: `ffmpeg` no se usó en esta fase porque no se generó ningún FLAC nuevo que validar externamente (no aplicaba la Sección 30). DirectWave/FL Studio: sigue UNKNOWN, sin cambios.

**LIMITACIONES**
Ver §22 del cuerpo principal: ausencia del `.wav` real es la limitación estructural central de toda la fase.

**RIESGOS**
Ninguno nuevo introducido por esta fase (solo documentación y tests aditivos, sin cambios de comportamiento). Riesgo preexistente sin cambios: sin corrida de CI real sobre nada de lo construido desde `Doc/26` en adelante.

**PRÓXIMOS PASOS**
Ver §23 del cuerpo principal: conseguir un `.wav` real de referencia es el único paso que puede cerrar esta pregunta con certeza.

---

# 🟡 FASE 2.1 — AUDITORÍA COMPLETADA CON LIMITACIONES

No se alcanza 🟢 porque la pregunta central (¿es realmente Float32?) queda en 🟡 PARTIALLY CONFIRMED, no en CONFIRMED -- la limitación es la ausencia del `.wav` real, no un vacío de esfuerzo de auditoría. No es 🔴 porque sí se alcanzó una conclusión clara, evidenciada y en capas, con una corrección real de documentación que antes sobreafirmaba certeza, más las dos matrices formales y la justificación explícita de la Sección 22 que esta adenda añade.
