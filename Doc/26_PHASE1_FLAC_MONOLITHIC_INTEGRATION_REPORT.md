# 26 — FASE 1: INTEGRACIÓN, VALIDACIÓN Y ENDURECIMIENTO DE FLAC + MONOLITHIC DWP

**Fecha:** 2026-09-20
**Punto de partida:** `Doc/25_PRE_CI_AUDIT_AND_DOCUMENTATION_PASS.md`, adenda 2 (run #26 de CI: 186 tests ejecutados, 4 fallos reales encontrados y corregidos ahí mismo; sin corrida posterior que confirme el estado verde).
**Entorno de esta fase:** contenedor de trabajo sin `.git` real (extracción de un `.zip` subido, no un checkout), sin Gradle Wrapper, sin Kotlin/kotlinc, sin Android SDK, sin acceso de red (egress bloqueado, confirmado con `curl` → HTTP 403, y con `apt-get install kotlin` → 403 real al intentar descargar del repositorio). Confirmado explícitamente antes de tocar cualquier archivo (Sección 9/29 del prompt de esta fase). **Sí** disponía de `ffmpeg 6.1.1` y Python 3, usados para la validación externa descrita en §8.

---

## 1. Objetivo

Continuar desde el estado real del repositorio (auditoría de FASE 0 ya realizada en pasadas anteriores) y llevar FLAC + Monolithic DWP a un estado más verificado: auditar el código real, ejecutar lo que el entorno permita ejecutar realmente, corregir únicamente fallos demostrados con evidencia, ampliar la cobertura de tests donde la Sección 11 del prompt de esta fase la exigía y no existía, y documentar honestamente qué quedó confirmado, qué sigue siendo hipótesis y qué falta.

## 2. Estado inicial

Exactamente el descrito en `Doc/18_CANONICAL_STATE.md` (antes de esta fase) y `Doc/25` adenda 2: 186 métodos `@Test` en 13 archivos (125 Core, ejecutados y pasados dos veces en CI real; 61 Monolithic+FLAC, ejecutados una vez en CI real con 4 fallos, corregidos pero sin confirmación posterior). Core estable, sin tocar desde `Doc/22`. Monolithic DWP y FLAC escritos e integrados desde `Doc/24`, auditados de nuevo en `Doc/25`.

## 3. Auditoría previa

Se auditó, leyendo el código real completo (no por memoria ni por la documentación existente), sin modificar nada durante la lectura:

**FLAC** (`domain/flac/`, 12 archivos): `FlacBitWriter.kt`, `FlacBitReader.kt`, `Crc.kt`, `FlacUtf8.kt`, `FlacFixedPredictor.kt`, `FlacRiceCoder.kt`, `FlacFrameEncoder.kt`, `FlacMetadataWriter.kt`, `FlacStreamWriter.kt`, `FlacDecoder.kt`, `FlacEncoder.kt`, `FlacPcmAudio.kt`. Resultado: bit writer/reader, CRC-8/CRC-16 (vectores cruzados de forma independiente en Python, ver §8, coinciden exactamente con `CrcTest.kt`), UTF-8 extendido, predictores FIXED, Rice coding (incluida la vía de escape), ensamblado de frame y de stream, STREAMINFO, y decoder, son consistentes entre sí y con la especificación pública de FLAC — **con una excepción real, descrita en §7**.

**Monolithic DWP** (`domain/dwp/monolithic/`, 7 archivos): `PcmNormalizer.kt`, `MonolithicDwpStructureBuilder.kt`, `MonolithicDwpAudioBuilder.kt`, `MonolithicDwpBuilder.kt`, `DwpBinaryAssembler.kt`, `MonolithicDwpValidator.kt`, `MonolithicDwpException.kt`. Resultado: consistente con lo documentado en `Doc/24`/`Doc/25`; el `Validator` sigue siendo explícito en sus errores (sin `catch (Exception) { return false }` que oculte causas); no se encontró ningún problema nuevo en esta capa.

No se modificó ningún archivo durante esta auditoría inicial, tal como exige la Sección 8 del prompt de esta fase.

## 4. Cambios realizados

1. **Corrección de un bug real de producción** en el límite de `blockSize` de FLAC (ver §7/§8/§18).
2. **15 tests nuevos** en `FlacEncoderRoundTripTest.kt`, cerrando exactamente la brecha de cobertura que la Sección 11 de esta fase exigía y que no existía: boundaries exactos de block size (511/512/513/4095/4096/4097), combinaciones mono/stereo × profundidad que faltaban (stereo 8-bit, stereo 24-bit, mono 32-bit a nivel de códec), tamaños de stream exactos de 2 y 3 muestras, valores límite de 8-bit y 24-bit, y 2 tests de regresión del bug de §7.
3. **Validación externa real** del algoritmo FLAC (nueva capacidad para este proyecto, nunca antes posible por falta de herramientas en el entorno — ver §8).
4. **Documentación actualizada**: `Doc/18_CANONICAL_STATE.md` (§E, §H, §I y cabecera) y `README.md`, con los conteos y el estado reales tras esta fase.

No se tocó el Core. No se tocó `MonolithicDwpValidator`, `MonolithicDwpBuilder`, ni ningún archivo de `domain/dwp/monolithic/` — la auditoría de esa capa (§3) no encontró ningún problema que lo justificara.

## 5. Archivos modificados

- `app/src/main/java/com/jvk/dwpcreator/domain/flac/FlacEncoder.kt` — corrección del límite de `blockSize` (§7).
- `app/src/main/java/com/jvk/dwpcreator/domain/flac/FlacFrameEncoder.kt` — misma corrección, por consistencia (§7).
- `app/src/test/java/com/jvk/dwpcreator/domain/flac/FlacEncoderRoundTripTest.kt` — 15 tests nuevos (de 15 a 30).
- `Doc/18_CANONICAL_STATE.md` — §E, §H, §I y cabecera de fecha, actualizados con el estado real tras esta fase.
- `README.md` — conteo de tests y estado de Monolithic+FLAC actualizados.

## 6. Archivos nuevos

- `Doc/26_PHASE1_FLAC_MONOLITHIC_INTEGRATION_REPORT.md` (este documento).

## 7. Archivos eliminados

    NONE

## 8. FLAC

### Bug real encontrado (auditoría de código, confirmado matemáticamente)

`FlacEncoder.encode()` y `FlacFrameEncoder.encodeFrame()` aceptaban `blockSize` en el rango `1..0x10000` (hasta 65536). Sin embargo, `FlacMetadataWriter.StreamInfo` (que escribe el bloque STREAMINFO) exige que `minBlockSize`/`maxBlockSize` estén en `0..0xFFFF` (0..65535) — son campos de 16 bits **sin** offset de -1, a diferencia del campo de tamaño de bloque del frame header (que sí usa un código de escape "16 bits = blocksize-1", pudiendo representar hasta 65536 en un frame aislado). Si `encode()` se llamaba con `blockSize = 65536` y el audio tenía suficientes muestras para que el encoder llegara a usar realmente ese tamaño de bloque completo, la ejecución fallaba con un `IllegalArgumentException` opaco muy dentro del pipeline (`StreamInfo.init`), en vez de un rechazo claro en la frontera pública.

**Corrección aplicada:** el límite se bajó a `1..0xFFFF` en ambos sitios (`FlacEncoder.kt`, `FlacFrameEncoder.kt`), con un mensaje de `require()` que explica la razón exacta (el campo de 16 bits de STREAMINFO), en vez de dejar que el fallo ocurriera tarde y sin contexto.

**Tests de regresión añadidos:** `encoding with blockSize=0x10000 (65536) is rejected` (verifica el rechazo inmediato con `IllegalArgumentException`) y `encoding with the maximum valid blockSize (0xFFFF, 65535) succeeds` (verifica que el límite correcto sigue funcionando).

### Implementación

Sin cambios de diseño; auditada de nuevo íntegra (§3), consistente con la especificación pública de FLAC salvo por el bug ya corregido.

### Pruebas

30 métodos `@Test` en `FlacEncoderRoundTripTest.kt` (antes 15), más 8 en `CrcTest.kt`, 5 en `FlacBitWriterReaderTest.kt`, 6 en `FlacFixedPredictorTest.kt`, 7 en `FlacRiceCoderTest.kt` — 56 tests del subsistema FLAC en total. Ninguno de los 15 nuevos se ha ejecutado todavía en Gradle/CI real (ver §14).

### Round-trip

Verificado a nivel de test unitario (PCM → `FlacEncoder` → bytes FLAC → `FlacDecoder` → PCM, comparando el contenido real, no solo ausencia de excepción) para: mono/stereo en 8/16/24-bit, mono 32-bit (a nivel de códec, ver nota en §11 abajo), silencio, señal constante, rampa lineal, ruido pseudoaleatorio determinista (semillas fijas), valores extremos alternantes, 8 canales, y ahora también los 6 boundaries exactos de block size y los tamaños de stream de 2/3 muestras. Estos tests están escritos y son correctos por lectura/auditoría, pero **no ejecutados por Gradle** en este entorno (ver §14).

### Validación externa

Realizada por primera vez en este proyecto (Sección 13 del prompt de esta fase). El entorno de este contenedor no tiene Kotlin/Gradle, pero sí `ffmpeg` (decoder FLAC independiente de este proyecto). Procedimiento:

1. Se reimplementó en **Python puro**, leyendo directamente el código Kotlin fuente (`Crc.kt`, `FlacBitWriter.kt`, `FlacUtf8.kt`, `FlacFixedPredictor.kt`, `FlacRiceCoder.kt`, `FlacFrameEncoder.kt`, `FlacMetadataWriter.kt`), el mismo algoritmo bit a bit.
2. Con esa reimplementación se generaron streams `.flac` reales (bytes en disco) para: mono 16-bit (3000 muestras, semilla 2026), stereo 24-bit (2500 frames, semilla 99), los 6 boundaries de block size (511/512/513/4095/4096/4097), 8-bit extremo (-128/127), 24-bit extremo (-8388608/8388607) y mono 32-bit acotado (±2²⁴, 600 muestras, semilla 50).
3. Cada archivo se decodificó con:
       ffmpeg -y -loglevel error -i archivo.flac -f <s8|s16le|s32le> salida.raw
   (`ffmpeg version 6.1.1-3ubuntu5`).
4. Se comparó la salida decodificada contra el PCM de origen, muestra por muestra.

**Resultado: bit-exacto confirmado en todos los casos** (mono-16, stereo-24, los 6 boundaries, 8-bit, 24-bit, 32-bit — con block size efectivo ≥16, ver hallazgo abajo). `ffprobe` también confirmó que `sample_rate`, `channels`, `bits_per_raw_sample` y `duration_ts` leídos del STREAMINFO coinciden exactamente con los valores escritos.

**Esto NO es "el código Kotlin se ejecutó".** Es evidencia de que el algoritmo, tal como está escrito hoy en el código fuente Kotlin, produce un bitstream FLAC válido según un decoder real e independiente (`ffmpeg`/libavcodec). La ejecución real del Kotlin en Gradle/CI sigue pendiente y es una verificación distinta y necesaria (ver §14/§21).

Como verificación adicional de bajo costo, se cruzaron también los 4 vectores CRC-8/CRC-16 ya usados en `CrcTest.kt` (`"123456789"` → `0xf4`/`0xfee8`; `bytes(0..255)` → `0x14`/`0x3b7a`) contra la misma reimplementación Python: coinciden exactamente.

**Hallazgo honesto adicional (no solicitado, encontrado durante la validación externa):** `ffmpeg`/libavcodec **rechaza** streams FLAC cuyo `max_block_size` en STREAMINFO es menor que 16 muestras, con el error `"invalid max blocksize: N"` / `"Invalid data found when processing input"` — confirmado reproduciendo el caso exacto (7 muestras, un único frame parcial). El propio decoder de este proyecto (`FlacDecoder.kt`) **no** impone ese mínimo y decodifica esos streams sin problema — es decir, el round-trip interno (nuestro encoder + nuestro decoder) es consistente, pero eso **no demuestra compatibilidad con decoders reales** para streams muy cortos (menos de 16 muestras en el último bloque). No se modificó el códec por este hallazgo: no hay evidencia de que DirectWave/FL Studio tenga la misma restricción ni de que la ausencia de ella importe en la práctica (un instrumento real de audio con menos de 16 muestras totales en 44.1kHz es <0.4ms, un caso extremo poco realista, pero no imposible con un WAV de test deliberadamente diminuto). Documentado como riesgo de interoperabilidad **UNCONFIRMED**, no como bug — ver §19/§21.

## 9. Monolithic DWP

Sin cambios de código en esta fase (la auditoría de §3 no encontró ningún problema que lo justificara). Estructura, builder, validator y serializer: sin cambios respecto a `Doc/24`/`Doc/25`. Round-trip (`MonolithicDwpBuilderTest`) sigue cubriendo header/versión/preámbulo/bloques/tags/0x01F7/0x0206/0x0004/payload FLAC/orden/EOF, sin cambios.

## 10. 0x01F7

Sin cambios respecto a `Doc/25`. Estado: **PARTIALLY CONFIRMED**. Los campos +00 frameCount, +08 channels, +0C bytes/sample candidate, +10 sampleRate, +24 bits/sample siguen documentados con ese nivel de evidencia, no como especificación confirmada.

## 11. 0x0206

Sin cambios respecto a `Doc/25`. Estado: **IMPLEMENTATION** (hipótesis de implementación: `+0x00 u32 flacLength`, `+0x04 u32 reservado/desconocido`, `+0x08 FLAC stream`), **EVIDENCE: ninguna referencia binaria real** (`REAL_MONOLITHIC_REFERENCE = MISSING`, sin cambios), **UNKNOWN** si DirectWave lo interpreta así.

## 12. 0x0205

Sin cambios respecto a `Doc/25`/`Doc/24`. El código no escribe `0x0205`; `MonolithicDwpAudioBuilder.TAG_MONOLITHIC_AUDIO_UNSUPPORTED_ALT` documenta la constante sin ningún writer para ella. Decisión interna, **IMPLEMENTATION DECISION / UNCONFIRMED**, no especificación del formato.

## 13. Integración con aplicación

Sin cambios en esta fase. Sin cambios respecto a `Doc/24`/`Doc/25` en cuanto a la conexión (o ausencia de ella) con ViewModel/UI — fuera del alcance de esta auditoría, que se centró exclusivamente en `domain/flac/` y `domain/dwp/monolithic/` según lo pedido.

## 14. Tests

    Detected:  201 métodos @Test en 13 archivos (125 Core + 76 Monolithic/FLAC)
    Executed:  NOT EXECUTED en esta fase (sin Gradle/kotlinc/Android SDK/red en este contenedor — confirmado, ver cabecera)
    Passed:    NOT EXECUTED
    Failed:    NOT EXECUTED
    Skipped:   NOT EXECUTED

Desglose honesto por procedencia de la evidencia (no todo "NOT EXECUTED" significa lo mismo):
- **125 Core:** ejecutados y pasados en corridas reales anteriores de CI (runs #24 y #26); no re-ejecutados en esta fase porque el Core no se tocó.
- **61 Monolithic+FLAC previos a esta fase:** ejecutados en run #26 (4 fallos, corregidos en `Doc/25` §20); sin corrida posterior a esa corrección.
- **15 nuevos de esta fase:** nunca ejecutados en Gradle. Verificados por (a) lectura/auditoría matemática del código Kotlin y (b) reimplementación Python independiente + decodificación con `ffmpeg` real para los casos representativos (§8) — evidencia fuerte de que el *algoritmo* es correcto, no sustituto de ejecutar el *Kotlin* real.

## 15. Build

    testDebugUnitTest: NOT EXECUTED (sin Gradle Wrapper ni Gradle instalable sin red en este contenedor)
    assembleDebug:     NOT EXECUTED (sin Android SDK ni red en este contenedor)

Se intentó explícitamente instalar un compilador Kotlin vía `apt-get install kotlin` para poder ejecutar al menos los módulos de dominio puro-JVM (`domain/flac/`, `domain/dwp/monolithic/` no dependen de Android) fuera de Gradle: falló con **403 Forbidden** real al intentar descargar del repositorio — confirma que el egress está bloqueado también para este camino alternativo, no solo para Gradle/Android SDK.

## 16. APK

    NOT EXECUTED — depende de assembleDebug (§15).

## 17. Problemas encontrados

1. `FlacEncoder.encode`/`FlacFrameEncoder.encodeFrame` aceptaban `blockSize` hasta 65536, un valor que `FlacMetadataWriter.StreamInfo` nunca puede representar en STREAMINFO (campo de 16 bits sin offset, máximo real 65535) — fallo tardío y opaco en vez de rechazo claro. **Corregido** (§8/§18).
2. Faltaba cobertura de test explícitamente exigida por la Sección 11 del prompt de esta fase: boundaries exactos de block size, combinaciones stereo 8-bit/24-bit, tamaños de 2/3 muestras. **Corregido** (§8).
3. (Hallazgo, no bug de este proyecto) `ffmpeg`/libavcodec rechaza streams FLAC con `max_block_size < 16` en STREAMINFO, aunque el decoder propio del proyecto los acepta. No corregido — sin evidencia de que aplique a DirectWave, documentado como riesgo (§8/§19/§21).

## 18. Problemas corregidos

1. Límite de `blockSize` en `FlacEncoder.kt`/`FlacFrameEncoder.kt`: `1..0x10000` → `1..0xFFFF`, con mensaje explícito. 2 tests de regresión añadidos.
2. Cobertura de tests de la Sección 11: 15 tests nuevos en `FlacEncoderRoundTripTest.kt`.

## 19. Problemas pendientes

- Ninguna corrida de CI real posterior a las correcciones de `Doc/25` §20 ni a las de esta fase (§18) — los 201 tests actuales (incluidos los 76 de Monolithic+FLAC) siguen sin confirmación de ejecución conjunta verde.
- `REAL_MONOLITHIC_REFERENCE = MISSING` — sigue sin existir ningún `.dwp` Monolithic real de DirectWave/FL Studio contra el que confirmar `0x0206`.
- Riesgo de interoperabilidad no confirmado: streams FLAC con `max_block_size < 16` son rechazados por al menos un decoder real (`ffmpeg`/libavcodec); no se sabe si DirectWave tiene la misma restricción.

## 20. Riesgos

- Sin corrida de CI real desde `Doc/25` §20, existe la posibilidad (no evidencia, posibilidad) de que las correcciones de esa adenda o de esta fase introdujeran algún error de compilación o de tipos que solo Gradle/kotlinc detectarían — ninguna herramienta disponible en este entorno puede descartarlo con certeza total, aunque la reimplementación Python (§8) da fuerte evidencia indirecta de que la lógica en sí es correcta.
- El hallazgo de `blockSize < 16` (§8) es un riesgo de interoperabilidad real con decoders de terceros, de alcance y probabilidad de ocurrencia desconocidos hasta que se pruebe con audio real de instrumentos (samples de más de unas pocas muestras son la norma, pero no está garantizado).

## 21. Limitaciones

- Entorno de trabajo sin Gradle/Kotlin/Android SDK/red, igual que en todas las fases anteriores desde `Doc/23` en adelante — documentado, no oculto.
- La validación externa de §8 prueba el *algoritmo*, no el *código Kotlin ejecutándose*; ambas cosas son necesarias y solo la segunda cierra definitivamente la verificación de esta capa.
- Compatibilidad real con DirectWave/FL Studio para el subsistema Monolithic DWP sigue sin poder confirmarse ni negarse — limitación estructural (falta de referencia real), no de esta fase.

## 22. Estado final de FASE 1

FASE 1 avanza el estado de Monolithic DWP + FLAC de "escrito, auditado y parcialmente ejecutado en CI real, con 4 fallos ya corregidos" a "escrito, auditado dos veces más (código + validación externa independiente), un segundo bug real de producción corregido, cobertura de tests ampliada exactamente donde el prompt de esta fase la exigía, y con la primera evidencia externa real de que el bitstream FLAC producido es válido según un decoder independiente" — sin poder todavía afirmar "verificado por ejecución real de Gradle/CI", que sigue siendo el paso que cierra esta capa definitivamente.
