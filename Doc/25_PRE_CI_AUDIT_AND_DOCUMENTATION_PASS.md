# 25 — PASS PRE-CI: AUDITORÍA Y CONSISTENCIA DOCUMENTAL

**Fecha:** 2026-09-17
**Naturaleza de este documento:** no es una nueva fase de implementación ni
de investigación. Es un Pass de auditoría documental y de consistencia
sobre la implementación de Monolithic DWP + FLAC ya escrita en
`Doc/24_MONOLITHIC_DWP_FLAC_IMPLEMENTATION.md`, previo a la primera
ejecución real de CI (GitHub Actions). No repite la investigación del
formato DWP/FLAC ya realizada en `Doc/00`–`Doc/24`.

---

## 1. Estado inicial (antes de este Pass)

- Core estabilizado y verificado por CI real (`Doc/22`): 125/125 tests
  pasando, APK generado, 0 warnings.
- Monolithic DWP + FLAC implementado (`Doc/23` especificación, `Doc/24`
  implementación): 19 archivos Kotlin de producción nuevos, 8 archivos de
  test nuevos, código nunca compilado ni ejecutado en un entorno real
  (sin Android SDK/Gradle/red en el entorno donde se escribió).
- `Doc/18_CANONICAL_STATE.md` seguía describiendo el estado **anterior**
  a `Doc/24`: afirmaba "Monolithic DWP — NOT IMPLEMENTED", "FLAC — NOT
  IMPLEMENTED", "31 archivos main / 5 test", "125 `@Test` en 5 archivos".
  Esto era correcto cuando se escribió, pero quedó desactualizado por
  `Doc/24` sin que nadie lo corrigiera — es exactamente la inconsistencia
  que este Pass existe para encontrar y corregir.
- `README.md` distinguía correctamente Core (verificado) de Monolithic+FLAC
  (no verificado), pero su última frase seguía citando "125 tests en 5
  archivos" como si fuera "el conteo agregado vigente", lo cual dejó de
  ser cierto en cuanto se agregaron los 8 archivos de test nuevos.
- `Doc/24` §9 afirmaba "~90 casos" de test nuevos, una aproximación nunca
  verificada contando los métodos `@Test` reales.

## 2. Auditoría realizada

Se leyó el código real (no solo la documentación) en:

- `app/src/main/java/com/jvk/dwpcreator/domain/dwp/monolithic/` (7 archivos)
- `app/src/main/java/com/jvk/dwpcreator/domain/flac/` (12 archivos)
- `app/src/test/java/com/jvk/dwpcreator/domain/dwp/monolithic/` (3 archivos)
- `app/src/test/java/com/jvk/dwpcreator/domain/flac/` (5 archivos)
- `Doc/18`, `Doc/23`, `Doc/24`, `README.md`, `Doc/05` (para la clasificación
  de `0x01f7`), `build.gradle.kts` / `app/build.gradle.kts` /
  `settings.gradle.kts` / `gradle.properties`, `.github/workflows/build.yml`.

Se recontaron directamente sobre el código (`grep`/`find`, no estimación):

| Métrica | Valor real contado | Valor que decía la documentación antes de este Pass |
|---|---|---|
| Archivos Kotlin `main` | 50 (31 Core + 19 nuevos) | `Doc/18`: 31 (desactualizado); `Doc/24`: 19 nuevos (correcto) |
| Archivos Kotlin `test` | 13 (5 Core + 8 nuevos) | `Doc/18`: 5 (desactualizado); `Doc/24`: 8 nuevos (correcto) |
| Métodos `@Test` totales | **186** | No se afirmaba en ningún documento como total agregado |
| Métodos `@Test` Core | 125 (`DwpEngineTest` 48, `WavDecoderTest` 28, `MidiMessageParserTest` 29, `ZipProjectIoTest` 14, `PcmConverterTest` 6) | `Doc/18`: 125 (correcto, sin cambios) |
| Métodos `@Test` nuevos (Monolithic+FLAC) | **61** (`CrcTest` 8, `FlacBitWriterReaderTest` 5, `FlacFixedPredictorTest` 6, `FlacRiceCoderTest` 7, `FlacEncoderRoundTripTest` 15, `PcmNormalizerTest` 6, `MonolithicDwpBuilderTest` 9, `MonolithicDwpValidatorTest` 5) | `Doc/24` §9: "~90" (**incorrecto**, corregido en este Pass) |
| Gradle Wrapper | Ausente (confirmado, `find . -iname "gradlew*"` → 0 resultados) | `Doc/18` §F: ausente, documentado como limitación de entorno (correcto, sin cambios) |
| Workflow de CI | `.github/workflows/build.yml` presente, usa `gradle` vía `gradle/actions/setup-gradle@v4` (no wrapper), ejecuta `testDebugUnitTest` y luego `assembleDebug`, resume resultados desde los XML de JUnit reales | Consistente con lo documentado |

**Comparación IMPLEMENTACIÓN REAL vs Doc/23 vs Doc/24 vs README vs Doc/18:**
la única contradicción numérica real encontrada fue el conteo de tests
nuevos ("~90" en `Doc/24` vs 61 real). El resto de contradicciones eran de
**vigencia** (`Doc/18` describiendo un estado anterior a `Doc/24` sin
haberse actualizado), no de exactitud dentro de su propio contexto temporal.

## 3. Problemas encontrados

1. **`Doc/18_CANONICAL_STATE.md` desactualizado respecto a `Doc/24`.**
   Secciones B, D, E, H, I y el párrafo final de "Estado del Core" seguían
   describiendo Monolithic DWP/FLAC como "NOT IMPLEMENTED" y los conteos de
   archivos/tests como si `Doc/24` no existiera. Causa: `Doc/24` nunca
   actualizó el documento canónico agregado al terminar. Impacto: cualquiera
   que leyera solo `Doc/18` (su propósito declarado: "única fuente de verdad
   agregada del proyecto") habría concluido que Monolithic+FLAC no existe.
2. **`README.md`, última frase de la sección de CI, desactualizada.**
   Citaba "125 tests en 5 archivos" como "el conteo agregado vigente" tras
   la integración de 8 archivos de test nuevos. Impacto: menor (el resto del
   README ya distinguía correctamente Core vs Monolithic+FLAC), pero esa
   frase específica era objetivamente incorrecta tras `Doc/24`.
3. **`Doc/24` §9, cifra "~90 casos" nunca verificada.** El valor real,
   contado método por método, es 61. Causa: aproximación hecha al escribir
   el documento sin contar los métodos `@Test` reales. Impacto: es
   exactamente el tipo de cifra no verificada que la Sección 2 del prompt
   maestro de este Pass pedía corregir explícitamente.

No se encontraron bugs de implementación demostrables durante esta
auditoría (ver §8-10 más abajo): el código de `domain/flac/` y
`domain/dwp/monolithic/` es internamente consistente con lo que `Doc/23`/
`Doc/24` describen, mantiene las clasificaciones de confianza correctas
(`0x0206` como 🔴 no confirmado, `0x01f7`+12 como 🟡 parcialmente
confirmado en `Doc/05`, sin elevarlo a 🟢), y no toca ningún archivo del
Core.

## 4. Correcciones realizadas

1. `Doc/18_CANONICAL_STATE.md`: reescritas las secciones B, D, E, H, I y el
   párrafo final de "Estado del Core", preservando literalmente el texto
   histórico anterior entre comillas donde aplicaba, y añadiendo notas
   explícitas "Actualizado en el Pass PRE-CI (`Doc/25`)" para que quede
   trazable qué decía antes y por qué se corrigió. No se borró ninguna
   frase histórica: se citó o se marcó como superada, nunca se eliminó sin
   dejar rastro.
2. `README.md`: reemplazada la última frase (conteo "vigente" incorrecto)
   por un párrafo que distingue explícitamente 186 presentes / 125 Core
   ejecutados / 61 nuevos no ejecutados, con la advertencia explícita de no
   leer "186" como "186 pasando".
3. `Doc/24_MONOLITHIC_DWP_FLAC_IMPLEMENTATION.md` §9: corregido "~90 casos"
   por "61 métodos `@Test`" con el desglose exacto por archivo, y una nota
   explicando que la cifra original era una aproximación incorrecta,
   corregida en este Pass (no se reescribió como si el error nunca hubiera
   existido).

Ningún archivo de `domain/flac/`, `domain/dwp/monolithic/` ni del Core fue
modificado. `Doc/23` no fue modificado (sigue representando correctamente
la arquitectura y las decisiones de diseño previas a la implementación; las
diferencias de nombres de clase entre la propuesta de `Doc/23` §7.2 —p. ej.
`DwpAudioBuilder`, `MonolithicZoneBuilder`, `DwpZone`, validadores
separados— y lo realmente construido en `Doc/24` §4 —`MonolithicDwpAudioBuilder`,
`MonolithicDwpStructureBuilder`, `MonolithicDwpValidator` consolidado, sin
`DwpZone`/`GlobalTemplateBuilder`— ya están explicadas por el propio
`Doc/24` como decisiones de la fase de implementación real; no es una
contradicción no documentada, así que no requiere reescribir `Doc/23`).

## 5. Archivos creados

- `Doc/25_PRE_CI_AUDIT_AND_DOCUMENTATION_PASS.md` (este documento).

## 6. Archivos modificados

- `Doc/18_CANONICAL_STATE.md`
- `README.md`
- `Doc/24_MONOLITHIC_DWP_FLAC_IMPLEMENTATION.md`

## 7. Archivos eliminados

**Ninguno.** `ELIMINADOS = 0`, como exige la Sección 22 del prompt maestro
de este Pass.

## 8. Estado del Core

🟢 **CORE STABLE — sin cambios en este Pass.** 125/125 tests verificados
previamente por ejecución real de CI (`Doc/22`, run #24). No se tocó
`DwpEngine`, `DwpTokenizer`, `DwpBlock`, `DwpDocument`, `DwpVersionProfile`
ni `WavDecoder`. No se encontró ninguna incompatibilidad real que
justificara tocar el Core.

## 9. Estado de FLAC

🟡 **Escrito e integrado, validación Gradle/Android pendiente.** 12
archivos en `domain/flac/`. Predictores FIXED 0-4 sin LPC (decisión
deliberada, documentada, no tratada como error). Decoder de verificación
(no genérico) con contrato de excepción único (`FlacDecodingException`).
CRC-8/CRC-16 verificados contra vectores públicos de terceros (evidencia
indirecta, no sustituye ejecución real en Kotlin/JUnit). 0 tests ejecutados
en Gradle hasta la fecha de este documento.

## 10. Estado de Monolithic DWP

🟡 **Escrito e integrado, validación Gradle/Android pendiente.** 7 archivos
en `domain/dwp/monolithic/`. Separación por composición respecto al Core
mantenida (verificado leyendo el código: ningún archivo del Core importa ni
referencia clases de `domain/dwp/monolithic` ni `domain/flac`). Inserción
de `0x0206` inmediatamente antes de `0x0004`, determinista, con rechazo
explícito de estructuras ambiguas (más de un terminador, más de un bloque
de audio embebido, más de un `0x01f7`). 0 tests ejecutados en Gradle hasta
la fecha de este documento.

## 11. Estado de 0x01F7

🟡 **HIPÓTESIS / CANDIDATO para el offset +12 (bytes por sample) — sin
cambios respecto a `Doc/05`.** Los offsets 0 (frame count), 8 (canales), 16
(sample rate float32) y 36 (bits por sample) están 🟢 **CONFIRMED** en
`Doc/05_DWP_IMPLEMENTATION_AUDIT.md` (evidencia cruzada contra un `.wav`
real). El offset +12 permanece 🟡 **PARTIALLY CONFIRMED**: coincide con el
`bytesPerSample` real del único fixture disponible, pero no existe un
segundo archivo de referencia con un bit-depth distinto que permita
descartar que ese byte codifique otra cosa que, en este archivo particular,
también valga lo mismo por coincidencia. El código nuevo
(`MonolithicDwpStructureBuilder.patchAudioFormatBlock`) lo actualiza usando
el mismo offset y fórmula que ya usaba `DwpEngine.replaceSampleAudio`
(Core, sin cambios), **sin elevar su nivel de confianza** — sigue
tratándose como candidato, nunca como hecho confirmado del formato
DirectWave. No se encontró ningún punto del código o la documentación que
presentara +12 como confirmado sin matizar.

## 12. Estado de 0x0206

🔴 **NO CONFIRMADO respecto a DirectWave — sin cambios.** La implementación
(`MonolithicDwpAudioBuilder`) trata "payload de `0x0206` = stream FLAC
nativo sin envolver" explícitamente como la hipótesis de menor riesgo
documentada en `Doc/23` §11, no como un hecho verificado — así lo dice el
propio KDoc de la clase. No existe ningún `.dwp` Monolithic real de
referencia ni una prueba en DirectWave/FL Studio que confirme la estructura
exacta. `0x0205` no tiene ningún writer (fuera de alcance, documentado).
Esta clasificación se mantiene íntegra en `Doc/18` §H tras la actualización
de este Pass.

## 13. Tests

- **Tests presentes:** 186 (`grep -r "@Test" app/src/test --include="*.kt" | wc -l`, verificado en este Pass).
- **Tests Core:** 125, en 5 archivos.
- **Tests nuevos (Monolithic+FLAC):** 61, en 8 archivos.
- **Tests ejecutados en este Pass:** 0. Este Pass es documental; no se
  ejecutó ningún test (sin Android SDK/Gradle disponible en este entorno,
  igual que en la fase de `Doc/24`).
- **Tests pasados en este Pass:** 0 (no determinable sin ejecución).
- **Tests fallidos en este Pass:** 0 (no determinable sin ejecución).
- **Tests no ejecutados:** 186 en este Pass. De ellos, 125 **sí** cuentan
  con una ejecución real previa y exitosa en otro momento (CI, run #24,
  antes de que existiera el código de `Doc/24`); los 61 nuevos no tienen
  ninguna ejecución real hasta la fecha.

## 14. Build

- Gradle ejecutado: **NO**
- Android SDK disponible: **NO**
- `testDebugUnitTest` ejecutado: **NO**
- `assembleDebug` ejecutado: **NO**
- APK generado (en este Pass): **NO**

Este entorno de trabajo no tiene Gradle, Android SDK, `kotlinc` ni acceso
de red, igual que en la fase de `Doc/24`. Ninguna afirmación de este
documento presenta una ejecución que no ocurrió como si hubiera ocurrido.

## 15. Documentación

Revisados y confirmados consistentes entre sí tras las correcciones de
este Pass: `README.md`, `Doc/18_CANONICAL_STATE.md`, `Doc/23`, `Doc/24`,
este documento (`Doc/25`). Ningún documento histórico (`Doc/00`–`Doc/22`)
fue modificado ni eliminado.

## 16. Riesgos abiertos

Sin cambios respecto a `Doc/24` §12 — ninguno se cerró en este Pass:

- R-02/RISK-02, R-03, R-08 (`Doc/13_RISK_REGISTER.md`): huecos de
  conocimiento del formato DWP, fixture único de referencia.
- R-NUEVO-03 (`Doc/24` §12): el código de Monolithic+FLAC nunca compiló ni
  se ejecutó en un entorno real; puede contener errores de sintaxis/tipos
  que solo la primera corrida real de CI revelará.

## 17. Limitaciones

- Esta auditoría se hizo por lectura completa del código real y conteo
  directo con herramientas de línea de comandos (`grep`, `find`), no por
  ejecución de tests ni de Gradle — ese es exactamente el estado que este
  Pass debía dejar preparado, no adelantar.
- No se intentó demostrar ni descartar compatibilidad con FL Studio
  Desktop/Mobile — fuera de alcance de este Pass, como del anterior.
- No se agregó LPC, no se implementó `0x0205`, no se tocó UI ni
  `ZipProjectExporter` — todo explícitamente fuera de alcance (Sección 19
  del prompt maestro de este Pass).

## 18. Próximo paso

**GitHub Actions**, ejecutando:

```
gradle testDebugUnitTest
gradle assembleDebug
```

sobre el proyecto íntegro (186 `@Test`: 125 Core + 61 nuevos), para:

1. Confirmar que los 125 tests del Core siguen pasando junto a los 61
   nuevos (regresión).
2. Obtener la primera evidencia real de compilación/ejecución del código
   de `Doc/24` — cierra R-NUEVO-03 si es verde; si no, corregir con
   evidencia real, no con conjetura.
3. Confirmar `assembleDebug` y la generación del APK.

No se declara que CI haya pasado hasta que exista esa evidencia real.

---

## 19. ADENDA — Primera corrida real de CI (run #25) y corrección aplicada

**Fecha:** 2026-09-20. Esta adenda documenta la primera ejecución real de
`gradle testDebugUnitTest` en GitHub Actions sobre el código de `Doc/24`,
posterior a la redacción original de este Pass, y la corrección aplicada
con evidencia real del log — exactamente el escenario que R-NUEVO-03
(`Doc/24` §12) preveía como riesgo abierto.

### 19.1 Resultado real de CI (run #25, commit `eb2b0ae`)

- `gradle testDebugUnitTest` → **BUILD FAILED en 41s**, en la tarea
  `:app:compileDebugUnitTestKotlin` (falla de **compilación**, no de
  ejecución de tests: `org.jetbrains.kotlin.gradle.tasks.CompilationErrorException`).
- Consecuencia: 0 archivos de reporte JUnit XML generados
  (`app/build/test-results/testDebugUnitTest/*.xml` vacío) → el paso
  "Summarize test results" reportó correctamente "Tests detected: 0" y
  falló el job, tal como está diseñado para hacer ante una compilación
  rota (Sección 18 del prompt maestro original: "NO afirmes que Android
  SDK está disponible/tests ejecutados si no ocurrió" — el workflow
  cumplió esto: no hubo ningún falso verde).
- `gradle assembleDebug` **no llegó a ejecutarse** (el job se detuvo en el
  paso de tests).

### 19.2 Causa raíz (evidencia real del log, no inferida)

El compilador reportó, textual, en
`app/src/test/java/com/jvk/dwpcreator/domain/flac/FlacFixedPredictorTest.kt`,
líneas 38, 41, 48 y 55:

```
No value passed for parameter 'from'
No value passed for parameter 'until'
```

Verificado contra el código real: `FlacFixedPredictor.selectBestOrder`
(`domain/flac/FlacFixedPredictor.kt`) tiene la firma
`selectBestOrder(samples: IntArray, from: Int, until: Int, maxOrder: Int = MAX_ORDER)`
— `from`/`until` son obligatorios, sin valor por defecto. El único llamador
de producción (`FlacFrameEncoder.kt:92`) ya los pasa siempre de forma
explícita (`0, samples.size`). `FlacFixedPredictorTest.kt` llamaba a
`selectBestOrder(samples)` y `selectBestOrder(samples, maxOrder = 4)` — una
forma de la API que nunca existió con esa firma. Es un **error de test**
(llamada incompleta a la API real), no un error de lógica del predictor ni
del código de producción, y no afecta a ningún otro archivo: el compilador
no reportó ningún otro error fuera de estas 4 llamadas en este único
archivo.

### 19.3 Corrección aplicada

Se corrigieron las 4 llamadas en `FlacFixedPredictorTest.kt` para pasar
`0, samples.size` explícitamente, igual que ya hacía el único llamador de
producción — **sin cambiar la intención de ningún test** (todos siguen
evaluando `selectBestOrder` sobre el array completo de muestras, que era
la intención original evidente por el propio nombre y cuerpo de cada
caso) y **sin tocar la firma de producción** de `selectBestOrder`
(Sección 1 del prompt maestro: prohibido cambiar contratos públicos sin
necesidad demostrada; aquí la necesidad no existía — el llamador real ya
cumplía el contrato, solo el test estaba desalineado). No se modificó
ninguna expectativa (`assertEquals`, `assert`) de ningún test — solo la
llamada incompleta que impedía compilar.

Archivo modificado: `app/src/test/java/com/jvk/dwpcreator/domain/flac/FlacFixedPredictorTest.kt`.
Ningún otro archivo de producción ni de test fue tocado en esta corrección.

### 19.4 Estado tras esta corrección

**No se afirma que el build vaya a pasar.** Esta corrección elimina la
única causa de fallo de compilación identificada en el log real del run
#25. Puede existir otro error de compilación o de ejecución no descubierto
todavía porque el compilador se detuvo en el primer conjunto de errores
sin necesariamente llegar a analizar el resto del módulo de tests en la
misma pasada. El siguiente paso sigue siendo el mismo de la §18 original:
push → nueva corrida de GitHub Actions → leer el log real, sea cual sea el
resultado.

---

## 20. ADENDA 2 — Segunda corrida real de CI (run #26): compiló, 4 tests fallaron con evidencia real

**Fecha:** 2026-09-20. El run #26 (commit posterior a la Adenda 1) **sí
compiló**: `186 tests completed, 4 failed`. Es la primera vez que el
módulo de tests de Monolithic+FLAC compila y se ejecuta realmente. Los 4
fallos, con su causa raíz verificada contra el código real (y, en dos
casos, contra una réplica independiente en Python), fueron:

### 20.1 `FlacFixedPredictorTest > selectBestOrder picks order 1 for a linear ramp` — bug de TEST, no de código

**Causa raíz (verificada matemáticamente):** para una rampa lineal pura
(`samples[i] = 3i - 25`), la primera diferencia (orden 1) es constante
(=3) pero la **segunda diferencia (orden 2) es exactamente 0**. Replicado
en Python de forma independiente: coste absoluto total por orden =
`{0: 2659, 1: 147, 2: 0, 3: 0, 4: 0}`. `selectBestOrder` elige
correctamente el orden 2 (el primero en alcanzar el coste mínimo
recorriendo 0→4), no el 1. La expectativa original del test no tenía en
cuenta esto. **Corrección:** se actualizó la expectativa de `1` a `2` y se
renombró el test, documentando la verificación matemática inline. No se
tocó `FlacFixedPredictor.kt` (el algoritmo ya era correcto).

### 20.2 `FlacRiceCoderTest` — 2 fallos, bug REAL de producción en `FlacRiceCoder`

**Causa raíz (verificada por simulación bit-a-bit en Python del
encode/decode real):** el campo que indica cuántos bits sin comprimir usa
la vía de escape de Rice coding tiene **5 bits** (máx. representable: 31),
tal como exige la especificación de FLAC para `RESIDUAL_CODING_METHOD=0`.
`maxUnencodedBitWidth()` podía devolver hasta 63 sin validar ese límite;
al escribirse con `writeBits(bitWidth, 5)`, un valor como 32 se truncaba
silenciosamente (`32 and 0b11111 = 0`), corrompiendo el stream sin ningún
aviso — confirmado exactamente: el test con residuales de ±2.000.000.000
(que requieren 32 bits) producía un `bitWidth` leído de vuelta como `0`
tras decodificar, en vez de fallar con un error claro.

**Corrección (código de producción, `FlacRiceCoder.kt`):**
`maxUnencodedBitWidth()` ahora lanza `FlacEncodingException` explícita
cuando el residual requeriría más de 31 bits, en vez de truncar en
silencio. Verificado que esto no afecta el uso real del pipeline: con el
audio de 8/16/24-bit que soporta `PcmNormalizer`, el residual máximo de un
predictor FIXED de orden ≤4 nunca supera ~28 bits.

**Corrección de los 2 tests correspondientes:**
- `round trips pathological large alternating residuals`: la magnitud se
  redujo de ±2.000.000.000 (32 bits, fuera del dominio representable por
  el formato) a ±500.000.000 (sigue forzando la vía de escape, cabe en 31
  bits) — preserva la intención original del test (ejercitar la vía de
  escape con valores enormes) dentro del dominio que el formato puede
  representar.
- `round trips extreme Int boundary values` (renombrado a `rejects
  residuals that need more bits than the escape width field can
  represent`): en vez de esperar un round-trip imposible según la propia
  especificación de FLAC, ahora verifica que el codificador **rechaza
  explícitamente** `Int.MIN_VALUE`/`Int.MAX_VALUE` con
  `FlacEncodingException` — lo cual es correcto: representarlos exige
  32-33 bits, más de los 31 que admite el formato.

Ninguna de estas dos correcciones de test oculta un comportamiento
incorrecto: al contrario, exponen y verifican explícitamente el límite
real (antes silenciosamente violado) del formato.

### 20.3 `MonolithicDwpValidatorTest > validate detects a corrupted embedded FLAC payload` — bug REAL de producción, hueco de validación

**Causa raíz (verificada leyendo el código real):** `FlacEncoder` calcula
correctamente el MD5 del PCM de origen y lo escribe en STREAMINFO
(`computeMd5`, ya existente). `FlacDecoder` lo lee (`readRawBytes(16)`)
pero **nunca lo comparaba contra nada** — ni el propio decoder ni
`MonolithicDwpValidator` recalculaban el MD5 del PCM decodificado para
verificarlo. El test corrompe un byte en el medio del payload FLAC
completo; con solo 8 muestras, ese punto medio cae dentro de los 16 bytes
del campo MD5 de STREAMINFO — una zona que, a diferencia de los frames de
audio (protegidos por CRC-16), no tenía ninguna verificación. Por eso la
corrupción pasaba completamente inadvertida: no cambiaba el PCM
decodificado, no cambiaba `total_samples`, y nada más la comprobaba.

Esta es precisamente la responsabilidad que `Doc/23` §7.2 ya asignaba al
validador ("verificar... sample count, **MD5**, CRC") y que no se había
implementado — no una limitación deliberada documentada, sino un hueco
real.

**Corrección (código de producción):**
- `FlacEncoder.computeMd5` cambiada de `private` a pública (sin tocar su
  lógica): es una función pura, y `MonolithicDwpValidator` la necesita
  para recalcular el MD5 del PCM ya decodificado.
- `MonolithicDwpValidator.validateAudio` ahora recalcula ese MD5 y lo
  compara contra `decoded.streamInfo.md5`; si no coincide, agrega un
  issue. No se tocó ningún test — el test ya esperaba correctamente
  `ok=false`, era el código de producción el que le faltaba esta
  verificación.

### 20.4 Resumen de archivos tocados en esta adenda

- `app/src/main/java/com/jvk/dwpcreator/domain/flac/FlacRiceCoder.kt` (bug real corregido)
- `app/src/main/java/com/jvk/dwpcreator/domain/flac/FlacEncoder.kt` (visibilidad de `computeMd5`, sin cambio de lógica)
- `app/src/main/java/com/jvk/dwpcreator/domain/dwp/monolithic/MonolithicDwpValidator.kt` (verificación de MD5 añadida, hueco real cerrado)
- `app/src/test/java/com/jvk/dwpcreator/domain/flac/FlacFixedPredictorTest.kt` (expectativa matemáticamente incorrecta corregida)
- `app/src/test/java/com/jvk/dwpcreator/domain/flac/FlacRiceCoderTest.kt` (2 tests corregidos para probar el dominio real representable)

Ningún archivo del Core fue tocado. No se afirma que el build vaya a
pasar en la próxima corrida — solo que estas 4 causas raíz, cada una
verificada contra el código real (y dos de ellas contra una réplica
independiente en Python), quedan corregidas. Siguiente paso: nuevo push,
nueva corrida real de CI, leer el log real.
