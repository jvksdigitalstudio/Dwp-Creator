# 24 — IMPLEMENTACIÓN: DWP MONOLITHIC + FLAC (primera versión)

> Continuación directa de `Doc/23_MONOLITHIC_DWP_FLAC_IMPLEMENTATION_SPEC.md`.
> Este documento describe **código real ya escrito** en esta fase, no otra
> ronda de especificación. Sigue vigente todo lo que `Doc/23` marca como
> 🔴 NO CONFIRMADO: esta implementación no inventa ninguna de esas
> incertidumbres, las hereda explícitamente (ver §Limitaciones).

## 1. Objetivo

Implementar, con código real e integrado al proyecto, la capacidad de
convertir un sample de un `.dwp` External (metadata + `.wav` externo) a
Monolithic (audio embebido, comprimido en FLAC, dentro del propio `.dwp`),
siguiendo exactamente la arquitectura de componentes separados que define
`Doc/23`.

## 2. Alcance de esta fase

- Un encoder FLAC completo y espec-válido (predictores fijos, sin LPC),
  con su propio decoder de verificación.
- El subsistema Monolithic DWP que envuelve ese FLAC en un bloque `0x0206`,
  lo inserta/reemplaza dentro de un `0x0003`, y preserva todo lo demás.
- Validación estructural y de audio post-construcción.
- Tests unitarios e de integración para todo lo anterior (**escritos**, ver
  §9 sobre por qué no están **ejecutados** todavía).
- Documentación de esta fase (este archivo) y actualización de `README.md`.

## 3. Fuera de alcance (sin cambios respecto a Doc/23)

- `0x0205` sigue sin implementarse (documentado como constante, sin writer).
- Ninguna decisión 🔴 de `Doc/23` se resolvió con evidencia nueva: donde este
  código toma una posición (p. ej. "el payload de `0x0206` es FLAC nativo sin
  envolver"), lo hace como la hipótesis de menor riesgo ya documentada, no
  como un hecho confirmado.
- Integración con `ZipProjectExporter`/UI: no se tocó ningún archivo de esa
  capa. `MonolithicDwpBuilder` es una capa de dominio independiente,
  invocable desde donde se decida en una fase de producto posterior.
- Ejecución de `gradle testDebugUnitTest`/`gradle assembleDebug` (ver §9).

## 4. Arquitectura implementada

```
domain/flac/                                  (nuevo — agnóstico de DWP)
├── Crc.kt                    CRC-8 / CRC-16 (polinomios públicos de FLAC)
├── FlacBitWriter.kt          escritor de bits MSB-first
├── FlacBitReader.kt          lector de bits MSB-first (inverso exacto)
├── FlacUtf8.kt                codificación UTF8-extendida del frame number
├── FlacFixedPredictor.kt     predictores fijos (órdenes 0-4) + selección
├── FlacRiceCoder.kt          codificación/decodificación Rice + escape
├── FlacMetadataWriter.kt     bloque STREAMINFO
├── FlacFrameEncoder.kt       un frame completo (header+subframes+CRC)
├── FlacStreamWriter.kt       ensamblado final (magic+STREAMINFO+frames)
├── FlacEncoder.kt            API pública: FlacPcmAudio -> stream FLAC
├── FlacDecoder.kt            API pública: stream FLAC -> FlacPcmAudio
│                              (decoder de verificación, no genérico — ver KDoc)
└── FlacPcmAudio.kt           contenedor PCM agnóstico de DWP/WAV

domain/dwp/monolithic/                        (nuevo — conoce DWP y WAV)
├── PcmNormalizer.kt                  WavDecoder.DecodedWav -> FlacPcmAudio
├── MonolithicDwpAudioBuilder.kt      PCM -> FLAC -> DwpBlock(0x0206)
├── MonolithicDwpStructureBuilder.kt  inserta/reemplaza dentro de un 0x0003
├── DwpBinaryAssembler.kt             DwpDocument -> bytes (envoltorio explícito)
├── MonolithicDwpValidator.kt         validación estructural + de audio
├── MonolithicDwpBuilder.kt           orquestador público de alto nivel
└── MonolithicDwpException.kt         jerarquía de errores propia
```

Ningún archivo del Core (`DwpBlock.kt`, `DwpDocument.kt`, `DwpTokenizer.kt`,
`DwpVersionProfile.kt`, `DwpEngine.kt`, `WavDecoder.kt`, `PcmConverter.kt`) se
modificó. `MonolithicDwpStructureBuilder` reutiliza `DwpTokenizer.tokenizeStrict`/
`serialize`/`readLE32`/`writeLE32` tal cual existen; `MonolithicDwpBuilder`
recibe un `DwpDocument` ya parseado por `DwpDocument.parse` (Core) y devuelve
otro `DwpDocument`, sin acoplarse a cómo se obtuvo ni a qué se hace con él
después.

## 5. Clases creadas

19 archivos Kotlin nuevos (12 en `domain/flac/`, 7 en `domain/dwp/monolithic/`),
listados completos en §4. Ninguna clase existente se dividió ni se eliminó.

## 6. Clases modificadas

**Ninguna clase de producción existente.** Cero cambios en `app/src/main` fuera
de los 19 archivos nuevos listados en §4.

## 7. Contratos principales

- `FlacEncoder.encode(pcm: FlacPcmAudio, blockSize: Int = 4096): ByteArray` —
  lanza `FlacEncodingException` si `pcm.frameCount == 0`.
- `FlacDecoder.decode(bytes: ByteArray): DecodedFlac` — **contrato de
  excepción único**: solo lanza `FlacDecodingException` ante cualquier entrada
  malformada (cualquier fallo de más bajo nivel se normaliza a esa excepción,
  ver §8 "Decisión técnica" sobre este punto).
- `PcmNormalizer.normalize(wav: WavDecoder.DecodedWav): FlacPcmAudio` — lanza
  `UnsupportedMonolithicFormatException` para `PCM_32`/`FLOAT_32` (ver §10).
- `MonolithicDwpBuilder.build(doc, sampleContainerIndex, wav, flacBlockSize, validate = true): BuildResult` —
  lanza `IllegalArgumentException` (índice fuera de rango),
  `UnsupportedMonolithicFormatException`, `MonolithicZoneStructureException` o
  `MonolithicDwpValidationException` según el fallo; nunca devuelve un
  documento parcialmente inconsistente sin lanzar.
- `MonolithicDwpValidator.validate(...): ValidationResult(ok, issues)` — no
  lanza por sí mismo (salvo `validateOrThrow`); reporta problemas como datos.

## 8. Decisiones técnicas (y su justificación)

1. **Predictores fijos (0-4), sin LPC.** FLAC válido y perfectamente
   reversible de todas formas; menor superficie de error en un componente que
   no pudo ejecutarse ni una sola vez antes de entregarse (ver §9). Verificado
   algorítmicamente (no solo "en teoría") — ver §9.
2. **Rice coding de partición única (partition order 0), con escape.**
   Misma justificación: simplicidad y verificabilidad antes que compresión
   óptima. El mecanismo de escape evita blow-up patológico.
3. **`sample rate`/`sample size` de frame header codificados como "obtener de
   STREAMINFO"** (códigos `0000`/`000`) en vez de tablas de valores comunes:
   evita mantener y validar esas tablas, sin ningún coste de validez.
4. **Blocksize de frame codificado siempre como "16-bit explícito al final
   del header"** (código `0111`): funciona para cualquier tamaño de bloque
   1..65536 sin lógica condicional adicional por rangos.
5. **`FlacDecoder.decode` normaliza todo fallo interno a `FlacDecodingException`.**
   Sin este ajuste, una STREAMINFO corrupta con un campo fuera de rango
   lanzaba `IllegalArgumentException` (desde las validaciones de
   `StreamInfo.init`) en vez del tipo de excepción documentado — se detectó
   escribiendo el test de corrupción en `MonolithicDwpValidatorTest` y se
   corrigió en el decoder, no parcheando el test para ocultarlo.
6. **`0x0206` se inserta siempre inmediatamente antes del `0x0004`
   terminador**, posición determinista (Doc/23 §27, hipótesis 🟡 explícita).
7. **`0x01f7`, si está presente, se actualiza** (frameCount/canales/
   bytesPerSample/sampleRate/bits) con los mismos 5 offsets que ya usa
   `DwpEngine.replaceSampleAudio` — implementado de forma **independiente**
   en `MonolithicDwpStructureBuilder` (no reutilizando ese método de
   `DwpEngine`, que tiene un contrato distinto: rename + replace). Es
   duplicación deliberada de 5 líneas, no una razón para acoplar ambos
   subsistemas (Sección 8 del prompt maestro).
8. **`DwpDocument` no sobrescribe `equals()`/`hashCode()` para su campo
   `preamble: ByteArray`** (comparación por identidad de referencia, no por
   contenido — la misma razón por la que `DwpBlock`, en el Core, sí lo hace).
   Se detectó al escribir los tests de esta fase (dos documentos
   independientemente parseados con el mismo contenido no eran `==` por esta
   razón) y se corrigió en `MonolithicDwpValidator`/tests comparando siempre
   por bytes serializados (`toBytes()` + `contentEquals`), exactamente el
   patrón que ya usa el resto de la suite de tests del Core (`grep` confirmó
   que ningún test existente compara `DwpDocument` con `==`/`assertEquals`
   directamente; todos usan `toBytes()`). Se corrigió también, por
   consistencia, en `FlacMetadataWriter.StreamInfo` (que sí tiene `equals`/
   `hashCode` propios ahora, sobre su campo `md5: ByteArray`). **No se tocó
   `DwpDocument` en el Core** — la corrección vive enteramente en el código
   nuevo de esta fase.

## 9. Tests

**TESTS ESCRITOS:** 8 archivos nuevos de test, **61 métodos `@Test`**
(conteo exacto por `grep -c "@Test"`, verificado independientemente en el
Pass PRE-CI de `Doc/25_PRE_CI_AUDIT_AND_DOCUMENTATION_PASS.md`; la cifra
"~90" que aparecía originalmente en esta sección era una aproximación
incorrecta hecha sin contar los métodos reales, y fue corregida en ese
Pass, no en esta redacción original):
`CrcTest` (8), `FlacBitWriterReaderTest` (5), `FlacFixedPredictorTest` (6),
`FlacRiceCoderTest` (7), `FlacEncoderRoundTripTest` (15), `PcmNormalizerTest` (6),
`MonolithicDwpBuilderTest` (9), `MonolithicDwpValidatorTest` (5).
Cubren: CRC contra
vectores públicos de terceros, predictores fijos y su inversa exacta, Rice
coding incluida la ruta de escape, round-trip FLAC completo (silencio,
constante, rampa, aleatorio, extremos, 8/16/24-bit, mono/estéreo/8 canales,
tamaños de bloque no múltiplos, corrupción de CRC/magic), normalización PCM
sin pérdida y rechazo explícito de formatos no soportados, construcción
Monolithic de extremo a extremo con preservación byte a byte de todo lo no
tocado, reemplazo idempotente, e inserción determinista, y detección de
corrupción/inconsistencia por el validador.

**TESTS EJECUTADOS: 0.** Este entorno de trabajo no tiene Android
SDK/Gradle/`kotlinc` ni acceso a red (verificado explícitamente antes de
empezar a escribir código: `which kotlinc/gradle` vacío, sin wrapper de
Gradle en el repo, sin conectividad para descargarlo). Sección 15 del prompt
maestro: **"si el entorno no tiene Android SDK/Gradle: NO afirmes que los
tests fueron ejecutados"** — no se afirma. `gradle testDebugUnitTest` y
`gradle assembleDebug` quedan pendientes de la primera corrida real en
GitHub Actions, igual que el resto del proyecto.

**TESTS PASADOS / FALLIDOS: no determinable todavía** (consecuencia directa
de lo anterior).

**Mitigación aplicada, más allá de lo mínimo exigible:** antes de escribir el
código Kotlin, se reimplementó el algoritmo completo (bit writer/reader, CRC,
predictores fijos, Rice coding con escape, frame header/subframes, UTF8 del
frame number) en Python, en un script aislado, y se corrió contra 12 casos de
prueba (silencio, constante, rampa, aleatorio con semillas fijas, extremos
alternados, 8/16/24-bit, mono/estéreo/8 canales, longitud no múltiplo de
blocksize, bloques pequeños forzando múltiples frames) — **los 12 pasaron**.
Además, los valores de CRC-8/CRC-16 se verificaron contra los vectores de
verificación públicos y conocidos de terceros CRC-8/SMBUS (`0xF4` para
`"123456789"`) y CRC-16/BUYPASS (`0xFEE8` para la misma cadena), que son
exactamente los parámetros (polinomio 0x07 sin reflejar / polinomio 0x8005
sin reflejar) que la especificación de FLAC exige para frame header/footer.
Esto da una confianza algorítmica alta, **pero no sustituye** la ejecución
real de la suite Kotlin/JUnit vía Gradle, que sigue pendiente.

## 10. Resultados

- Subsistema FLAC completo, agnóstico de DWP, con encoder y decoder de
  verificación.
- Subsistema Monolithic DWP completo: construcción, preservación,
  reemplazo idempotente, validación.
- 0 cambios en el Core estabilizado (`Doc/22`).
- Fixture `Instrument.dwp` no tocado (no leído ni modificado por ningún
  archivo de esta fase — verificable: ningún archivo nuevo referencia esa
  ruta).
- SHA-256 del fixture: sin cambios (no se ejecutó ninguna operación sobre él).

## 11. Limitaciones (explícitas, no ocultas)

- **No hay ningún `.dwp` Monolithic real de referencia.** Todo lo que esta
  implementación asume sobre la forma exacta del payload de `0x0206` sigue
  siendo la hipótesis de menor riesgo de `Doc/23`, no un hecho verificado.
  R-03/R-08 (`Doc/13_RISK_REGISTER.md`) siguen **abiertos**.
- **El fixture real (`Instrument.dwp`, 32-bit float) no puede ejercitar este
  pipeline end-to-end**, porque `PcmNormalizer` rechaza explícitamente
  `FLOAT_32`/`PCM_32` en esta fase (Doc/23 §15, sin evidencia para elegir un
  ancho de bits de destino). Los tests de integración usan un documento
  sintético con la misma forma estructural, 16-bit PCM.
- **Sin ejecución real de tests** (ver §9) — es el punto más importante de
  esta lista: "compila" y "los tests pasan" no se han demostrado todavía,
  solo argumentado con evidencia indirecta (réplica en Python + vectores CRC
  de terceros).
- **Sin LPC**: la compresión lograda es menor a la de un encoder FLAC de
  referencia (que sí usa LPC); el archivo resultante es válido pero más
  grande de lo necesario.
- **Sin compatibilidad demostrada con FL Studio** (Desktop o Mobile): fuera
  de alcance de esta fase por diseño (Doc/23 §17/§31).
- **`0x0205` no implementado.**

## 12. Riesgos (heredados + nuevos)

Los mismos de `Doc/23` §32 (R-02/RISK-02, R-03, R-08, R-NUEVO-01, R-NUEVO-02),
sin cambios de estado — ninguno se cerró en esta fase. Se añade:

| ID | Riesgo | Estado |
|---|---|---|
| R-NUEVO-03 | El código Kotlin de esta fase nunca compiló ni se ejecutó en este entorno; puede contener errores de sintaxis/tipos que solo la primera corrida real de CI revelará, pese a la verificación algorítmica en Python | Abierto — mitigar con la primera corrida de `gradle testDebugUnitTest` |

## 13. Trabajo pendiente (siguiente paso recomendado)

1. Ejecutar `gradle testDebugUnitTest` y `gradle assembleDebug` en GitHub
   Actions (o localmente con Android SDK) y corregir cualquier error de
   compilación/test que aparezca — es el paso que cierra R-NUEVO-03.
2. Confirmar los 125 tests del Core siguen pasando junto a los nuevos
   (regresión).
3. Conseguir un `.dwp` Monolithic real de referencia (o generarlo con
   herramientas de terceros ya identificadas en fases de investigación
   anteriores) para poder cerrar R-03/R-08 y confirmar o corregir la
   estructura de `0x0206`.
4. Decidir, como tarea de producto (fuera de esta capa de dominio), desde
   dónde de la UI/flujo de exportación se invoca `MonolithicDwpBuilder`.
5. Evaluar si soportar `PCM_32`/`FLOAT_32` en `PcmNormalizer` alguna vez,
   condicionado a que aparezca evidencia real de qué ancho de bits espera
   DirectWave para audio embebido.
