# 23 — ESPECIFICACIÓN DE IMPLEMENTACIÓN: DWP MONOLITHIC + FLAC

> **⚠️ CORRECCIÓN POSTERIOR (ver `Doc/28_PHASE2_1_FLOAT32_DWP_AUDIT.md`, FASE 2.1):** varias frases de este documento (§15, §17, §F) citan "32-bit float" como una característica confirmada del fixture real, al mismo nivel que otros elementos 🟢 CONFIRMADOS. Esa clasificación depende de un `.wav` real examinado en una fase muy anterior del proyecto (ver comentario de cabecera de `WavDecoder.kt`) y hoy no disponible en este repositorio — el propio `0x01F7` del `.dwp` (offset 12, "4 bytes/muestra") no distingue por sí solo PCM entero de 32 bits de IEEE float de 32 bits. `Doc/28` reclasifica esto como 🟡 PARTIALLY CONFIRMED, no como un error de esta especificación; la decisión de rechazar explícitamente ambos formatos en `PcmNormalizer` sigue siendo válida y ahora tiene una justificación más precisa.
>
> **Naturaleza de este documento:** especificación técnica de implementación, no investigación nueva y no código. Reconstruye y consolida en un único lugar todo lo ya establecido en `Doc/00`–`Doc/22` sobre el objetivo "DWP monolítico con audio embebido en FLAC", y define la arquitectura, el modelo de datos, el flujo de generación, los criterios de aceptación y el plan de tests que la **siguiente** fase (implementación real) deberá seguir. **Esta fase no escribe ningún encoder FLAC, ningún writer de `0x0205`/`0x0206`, ni toca `DwpEngine`/`DwpTokenizer`/`DwpDocument` de producción.**
>
> **Estado heredado obligatorio, verificado antes de escribir esta especificación:** Core Stabilization cerrado (`Doc/22_CORE_STABILIZATION_FINAL_VERIFICATION.md`): 125/125 tests detectados y ejecutados con éxito en CI real (run #24), `gradle assembleDebug` exitoso, APK probada en dispositivo real, 0 warnings, `Instrument.dwp` byte-idéntico (SHA-256 `d7699c3c...941925`), `RISK-02`/`R-08` abiertos, Monolithic DWP/FLAC **no implementado**. Este documento no repite esa verificación; la asume como punto de partida y la referencia donde corresponde.

---

## A. Trazabilidad de esta fase

**A.1 Documentos leídos íntegros (los 23 archivos de `Doc/`, incluido este mismo antes de escribirlo):**
`00_PROJECT_AUDIT.md`, `01_PROJECT_STRUCTURE.md`, `02_REAL_ARCHITECTURE.md`, `03_CODE_INVENTORY.md`, `04_EXECUTION_FLOW.md`, `05_DWP_IMPLEMENTATION_AUDIT.md`, `06_BINARY_FORMAT_MODEL.md`, `07_AUDIO_PIPELINE_AUDIT.md`, `08_MONOLITHIC_DWP_ANALYSIS.md`, `09_TEST_AUDIT.md`, `10_BUILD_AUDIT.md`, `11_DOCUMENTATION_AUDIT.md`, `12_KNOWN_ISSUES.md`, `13_RISK_REGISTER.md`, `14_KEEP_REFACTOR_REPLACE.md`, `15_AUDIT_CONCLUSIONS.md`, `16_PHASE1_CHANGES.md`, `17_AUDIT_ERRATA.md`, `18_CANONICAL_STATE.md`, `19_PHASE2_CORE_HARDENING.md`, `20_CORE_STABILIZATION_PASS2.md`, `21_CORE_STABILIZATION_PASS3.md`, `22_CORE_STABILIZATION_FINAL_VERIFICATION.md`.

Además, código fuente real revisado directamente (no solo por lo que la documentación dice de él) para verificar nombres exactos de tags/clases antes de citarlos aquí: `DwpBlock.kt`, `DwpVersionProfile.kt`, `DwpTokenizer.kt`, `DwpDocument.kt`.

**A.2 Documento creado en esta fase:** este mismo, `23_MONOLITHIC_DWP_FLAC_IMPLEMENTATION_SPEC.md`. No existía previamente ningún documento dedicado a la especificación de implementación de Monolithic+FLAC — `08_MONOLITHIC_DWP_ANALYSIS.md` es un análisis de brecha (qué falta y por qué), no una especificación de cómo construirlo; `15_AUDIT_CONCLUSIONS.md` §"Arquitectura objetivo propuesta" es un boceto de alto nivel, no una especificación. Se verificó explícitamente que ninguno de los 23 documentos existentes cumple ya este rol antes de crear uno nuevo (Sección 3 del prompt de esta fase).

**A.3 Documentos modificados en esta fase:** ninguno. No se ha detectado ninguna afirmación en `Doc/00`–`Doc/22` que esta especificación contradiga; todo lo que sigue es una **consolidación** de lo ya escrito, no una corrección de ello. Donde esta especificación añade una decisión de diseño nueva (p. ej. la forma exacta de `DwpZone`), se marca explícitamente como nueva, no como heredada.

**A.4 Código productivo tocado en esta fase:** ninguno. Cero cambios en `app/src/main`, cero cambios en `app/src/test`, cero dependencias nuevas.

---

## 1. Objetivo de implementación

Producir, en una fase **futura** de implementación, la capacidad de generar un `.dwp` **Monolithic** (audio embebido, comprimido en FLAC, dentro del propio archivo `.dwp`) a partir de un proyecto cargado hoy en formato External DWP (metadata + `.wav` externos), sin romper ninguna de las tres capacidades ya verificadas del Core: parseo, edición dirigida (rename, replace de metadata de audio) y reexportación como External DWP.

## 2. Alcance

- Diseño del modelo de datos, componentes y flujo necesarios para: leer PCM real (ya existe vía `WavDecoder`), codificarlo a FLAC, construir el/los bloque(s) DWP que transportan ese FLAC, insertarlos en el payload de un `0x0003` (sample container) existente, y serializar el documento resultante.
- Definición de qué se considera 🟢/🟡/🔴 sobre la estructura binaria objetivo (`0x0205`, `0x0206`, relación con `0x01f7`/`0x0004`).
- Definición de la arquitectura de componentes (nombres, responsabilidades, límites) que la implementación futura deberá seguir.
- Definición del plan de tests y de los criterios de aceptación que esa implementación futura deberá satisfacer antes de considerarse terminada.
- Definición de la política de preservación binaria para los tres escenarios de uso (creación, edición, transformación Standard→Monolithic).

## 3. Fuera de alcance (de esta fase, explícitamente)

- Escribir el encoder FLAC.
- Escribir cualquier `*Builder`/`*Writer`/`*Validator` nuevo mencionado en la arquitectura de la Sección 7.
- Modificar `DwpEngine`, `DwpTokenizer`, `DwpDocument`, `DwpBlock`, `WavDecoder`, `ZipProjectLoader`, `ZipProjectExporter` o cualquier otro archivo de producción.
- Confirmar la estructura binaria real de `0x0205`/`0x0206` contra un `.dwp` monolítico real — sigue bloqueado por `R-08`/`R-03` (`Doc/13_RISK_REGISTER.md`): no existe todavía ningún fixture monolítico de referencia en el proyecto.
- Ejecutar ningún test, nuevo o existente (no hay código nuevo que probar).
- Decidir si el resultado final reemplaza, coexiste con, o es alternativo al modelo "External DWP" en la UI — es una decisión de producto fuera del alcance técnico de esta especificación.

## 4. Arquitectura prevista

Ver desarrollo completo en la Sección 7. Resumen: capa de orquestación nueva (`DwpBuilder`) que reutiliza sin modificar la capa física existente (`DwpBlock`/`DwpTokenizer`/`DwpDocument`) y añade componentes nuevos y separados para: normalización PCM, codificación FLAC, construcción del bloque de audio embebido, construcción de zona, y validación — ninguno de ellos fusionado en un método monolítico único.

## 5. Flujo de generación (previsto, no implementado)

```
.wav real (por sample)
   │  WavDecoder.decode()                          [YA EXISTE, sin cambios]
   ▼
DecodedWav (PCM crudo + sampleRateHz + channelCount + bytesPerSample + frameCount)
   │  PcmNormalizer                                 [NUEVO]
   │    - valida/normaliza profundidad de bits de entrada hacia lo que
   │      FlacEncoder acepte (ver §9 y §15 para las decisiones abiertas)
   ▼
PCM normalizado
   │  FlacEncoder.encode()                          [NUEVO — mayor riesgo del proyecto]
   │    STREAMINFO → metadata blocks → audio frames → CRC/MD5
   ▼
Stream FLAC completo (bytes)
   │  DwpAudioBuilder                                [NUEVO]
   │    empaqueta el FLAC como payload de un bloque DWP nuevo (0x0206,
   │    ver §10 para su estado de evidencia) + actualiza/relaciona con
   │    0x01f7 y 0x0004 según §14
   ▼
DwpBlock(s) nuevos, listos para insertarse en el payload de un 0x0003
   │  MonolithicZoneBuilder                          [NUEVO]
   │    toma el 0x0003 existente (plantilla, ver §28) y produce una
   │    versión nueva de su lista de bloques hijos con el audio embebido
   │    insertado/sustituido, preservando todo lo demás verbatim
   ▼
DwpDocument nuevo (misma preamble, blocks con el/los 0x0003 modificados)
   │  DwpTokenizer.serialize()                       [YA EXISTE, sin cambios]
   ▼
.dwp Monolithic (bytes)
   │  Validators (ver §31)                           [NUEVOS]
   ▼
.dwp Monolithic validado, listo para escribir a disco / empaquetar
```

Cada flecha marcada `[YA EXISTE]` es exactamente el punto de reutilización ya identificado en `Doc/08_MONOLITHIC_DWP_ANALYSIS.md` §"Lo que SÍ existe y es reutilizable" — no se propone ningún cambio a esos componentes.

## 6. Modelo DWP interno

Se mantienen, sin modificar, los tres niveles ya existentes y verificados:

1. **Modelo físico** (`DwpBlock`/`DwpTokenizer`/`DwpDocument`, ya existe): tag/length/reserved/payload, LE32, sin offsets absolutos, lista plana con preservación de duplicados y orden.
2. **Modelo semántico de lectura** (`SampleInfo` vía `DwpEngine.listSamples()`, ya existe): DTO derivado, solo lectura.
3. **Modelo semántico de zona** (`DwpZone`, **nuevo**, no implementado): capa de más alto nivel propuesta en `Doc/15_AUDIT_CONCLUSIONS.md`, formalizada aquí (ver §7.3) como el punto donde un `MonolithicZoneBuilder` razona sobre "esta zona ahora tiene audio embebido" sin tener que conocer los bytes crudos de `0x0206` directamente en la capa de orquestación.

No se introduce ningún cuarto nivel ni se colapsan estos tres en uno solo.

## 7. Arquitectura (componentes, responsabilidades y límites)

### 7.1 Regla general

Ningún componente nuevo puede, a la vez, (a) construir el DWP, (b) codificar FLAC, (c) calcular metadatos, (d) escribir bytes crudos, (e) gestionar samples y (f) validar estructura. Cada responsabilidad vive en su propio componente, con un contrato de entrada/salida explícito. Esto es una restricción de diseño obligatoria para la fase de implementación futura (Sección 7 del prompt de esta fase), no una sugerencia.

### 7.2 Componentes nuevos (ninguno implementado en esta fase)

| Componente | Responsabilidad única | Entrada | Salida | Reutiliza |
|---|---|---|---|---|
| `PcmNormalizer` | Adaptar el PCM decodificado a lo que el encoder FLAC requiera (profundidad de bits soportada, orden de canales) — **sin** perder precisión salvo que sea estrictamente necesario y documentado | `DecodedWav` (de `WavDecoder`, ya existe) | PCM normalizado + metadata de formato | `WavDecoder` (lectura, no modificado) |
| `FlacEncoder` | Codificar PCM normalizado a un stream FLAC válido (STREAMINFO + metadata + audio frames + CRC/MD5) | PCM normalizado + sampleRate/channels/bitsPerSample | Bytes FLAC completos | Ninguno existente — componente enteramente nuevo, el de mayor riesgo (ver §32) |
| `DwpAudioBuilder` | Empaquetar el stream FLAC como el/los bloque(s) DWP que lo transportan, según la estructura confirmada en su momento para `0x0206` (hoy sin confirmar, ver §10) | Bytes FLAC + parámetros de audio | `List<DwpBlock>` (bloque(s) de audio embebido) | `DwpBlock` (modelo físico, no modificado) |
| `MonolithicZoneBuilder` | Tomar los bloques hijos actuales de un `0x0003` (vía `DwpTokenizer.tokenizeStrict`, ya existe) y producir la lista de bloques hijos resultante con el audio embebido insertado/sustituido, preservando todo lo demás | `List<DwpBlock>` (hijos actuales del `0x0003`) + bloques de `DwpAudioBuilder` | `List<DwpBlock>` (hijos nuevos) | `DwpTokenizer` (tokenización/serialización anidada, no modificado) |
| `GlobalTemplateBuilder` | Cargar un `.dwp` existente como plantilla base (ya es, de facto, lo que hace `ZipProjectLoader` + `DwpDocument.parse` hoy) — formaliza el rol, no añade lógica nueva | Bytes de un `.dwp` real | `DwpDocument` | `DwpDocument.parse` (no modificado) |
| `DwpZone` (modelo, no builder) | Representación semántica de una zona/sample con su mapping, identidad y audio (embebido o externo), para que `MonolithicZoneBuilder` no razone directamente sobre bytes crudos de `0x0206` | — (tipo de datos) | — | Deriva de `SampleInfo` existente, la extiende |
| `DwpStructureValidator` | Verificar round-trip físico + `endOffset` exacto por nivel (top y anidado) tras la construcción | `DwpDocument` construido | OK / lista de discrepancias | `DwpTokenizer.tokenizeStrict` |
| `DwpAudioValidator` | Verificar coherencia frameCount (lógico) ↔ tamaño real de audio (FLAC decodificado) | `DwpDocument` + PCM de referencia | OK / discrepancias | `FlacEncoder`/decoder de verificación |
| `FlacValidator` | Round-trip PCM→FLAC→PCM exacto, sample count, MD5, CRC | PCM original + FLAC generado | OK / discrepancias | `FlacEncoder` |
| `DwpConsistencyValidator` | Validación final agregada antes de escribir a disco (agrega los validadores anteriores) | `DwpDocument` completo | OK / lista de discrepancias | Los validadores anteriores |
| `DwpBuilder` | Orquestador de alto nivel que llama a los componentes anteriores en el orden del flujo de §5 — **no contiene lógica de codificación ni de validación propia**, solo orquesta | Proyecto cargado (`LoadedProject`, ya existe) + parámetros de exportación | `DwpDocument` Monolithic | Todos los anteriores |

### 7.3 `DwpZone` (modelo semántico nuevo, decisión de diseño de esta fase)

```
DwpZone
 ├── mapping: Mapping                  (deriva de 0x01f4 — root/low/high ya confirmado, Sección 05)
 ├── identity: SampleIdentity          (deriva de 0x01f5/0x01f6 — ya se lee hoy)
 ├── audio: ZoneAudio                  (sellado: External(0x01f7) | Embedded(0x0206, hoy sin confirmar))
 ├── unknownBlocks: List<DwpBlock>     (todo lo opaco de 0x01f8-0x0204, preservado sin interpretar)
 └── terminator: DwpBlock              (0x0004, preservado)
```

`ZoneAudio` se modela como tipo sellado (`External`/`Embedded`) precisamente para que el código que construye o lee una zona nunca pueda "olvidar" cuál de los dos casos aplica — decisión de diseño nueva de esta fase, no heredada de ningún documento anterior.

## 8. Core estable — restricciones que la implementación futura debe respetar

No debe romperse ninguno de los componentes listados como `KEEP` en `Doc/14_KEEP_REFACTOR_REPLACE.md`: `DwpBlock`, `DwpDocument`, `DwpTokenizer`, `DwpEngine`, `WavDecoder`, `ZipProjectLoader`, `ZipProjectExporter`, MIDI, `SamplePlayer`, y los 125 tests existentes (`Doc/22` §2). Toda nueva abstracción (Sección 7) se integra **por composición**, nunca modificando estas clases para que "sepan" de FLAC/Monolithic — el punto de inserción ya identificado en `Doc/08_MONOLITHIC_DWP_ANALYSIS.md` (dentro del `for` de `ZipProjectExporter.export`, donde hoy se llama `DwpEngine.replaceSampleAudio`) sigue siendo válido como el lugar donde un futuro `DwpBuilder`/`MonolithicZoneBuilder` se invocaría, sin que `ZipProjectExporter` necesite saber cómo se construye internamente un bloque `0x0206`.

## 9. FLAC — pipeline de decisiones (documentar, no implementar)

```
PCM de entrada (de WavDecoder, ya existe)
    ↓
normalización necesaria         → 🔴 NO CONFIRMADO qué profundidades de bits acepta DirectWave embebido; ver §32
    ↓
STREAMINFO                      → 🔴 NO CONFIRMADO contra un .dwp monolítico real; solo conocido en abstracto (spec pública de FLAC)
    ↓
metadata                        → 🔴 NO CONFIRMADO si DirectWave espera bloques METADATA adicionales (SEEKTABLE, VORBIS_COMMENT) o los rechaza/ignora
    ↓
audio frames                    → 🔴 NO CONFIRMADO nivel de compresión/parámetros de predicción esperados, si alguno es obligatorio
    ↓
FLAC completo                   → 🔴 NO CONFIRMADO si el payload es FLAC "nativo" (con magic `fLaC`) o un sub-formato empaquetado distinto
    ↓
payload DWP (0x0206 hipotético) → 🔴 NO CONFIRMADO, ver §10
```

Ninguna de estas decisiones se convierte en regla de implementación en este documento — cada una queda explícitamente 🔴, siguiendo la Sección 6 del prompt de esta fase ("no convertir un 🟡/🔴 en una afirmación absoluta").

## 10. `0x0205`

🔴 **NO CONFIRMADO.** Sin evidencia en ningún documento existente ni en el fixture disponible (`Instrument.dwp`, External DWP). `Doc/05_DWP_IMPLEMENTATION_AUDIT.md` confirma con evidencia binaria (`C-02` en `Doc/17_AUDIT_ERRATA.md`) que ni `0x0205` ni `0x0206` aparecen en ningún nivel del único fixture real disponible. Se documenta como **candidato** (por proximidad numérica a `0x0206` en el rango de tags observado, `0x01f4`-`0x0204`) a un posible bloque relacionado (p. ej. cabecera/metadata del bloque de audio embebido, o un bloque de anuncio de "este sample es monolítico"), pero esto es una hipótesis sin ningún respaldo, no una decisión.

## 11. `0x0206`

🔴 **NO CONFIRMADO.** Mismo estado que `0x0205`. Es el tag hipotético para el payload de audio embebido en sí (FLAC), mencionado como objetivo en el propio prompt de trabajo original pero sin ninguna evidencia de código, binaria o de terceros disponible dentro de este proyecto. `Doc/13_RISK_REGISTER.md` R-03 lo marca explícitamente como **ABIERTO — bloqueante**, y esta especificación no lo cierra ni lo intenta cerrar: documenta el punto de inserción arquitectónico (§7, `DwpAudioBuilder`/`MonolithicZoneBuilder`) donde iría **una vez confirmado**, sin inventar su estructura de bytes.

## 12. `0x0004`

🟢 **CONFIRMADO** como terminador de sample (longitud 0), ya presente y verificado en el modelo actual (`DwpBlock.TAG_SAMPLE_END`, `Doc/05_DWP_IMPLEMENTATION_AUDIT.md`). 🔴 **NO CONFIRMADO** si su rol cambia, se mantiene idéntico, o se duplica quese en un `0x0003` que además contenga un `0x0206` — no hay evidencia de un sample container monolítico real para verificarlo. Regla de implementación futura: `MonolithicZoneBuilder` debe preservar `0x0004` sin asumir ningún cambio de semántica hasta que haya evidencia real.

## 13. `0x01f7`

🟢 **CONFIRMADO** (evidencia completa en `Doc/05_DWP_IMPLEMENTATION_AUDIT.md`): offsets 0 (frameCount), 8 (canales), 16 (sampleRate como float32), 36 (bits por muestra); 🟡 offset 12 (bytes por muestra, candidato fuerte, sin segunda referencia cruzada); offsets 4/20/24/28/32 🔴 sin identificar. Relación con `0x0206` (audio embebido): 🔴 **NO CONFIRMADO** si en un archivo monolítico (a) `0x01f7` desaparece, (b) se mantiene con el mismo significado (frameCount lógico del PCM real, aunque el payload físico ahora sea FLAC comprimido), o (c) cambia de significado. **Regla de implementación futura, no evidencia:** la hipótesis de menor riesgo y más consistente con "no reinterpretar campos sin evidencia" (Sección 6 del prompt maestro de esta fase) es que `0x01f7` se **mantenga** con su significado actual (frameCount = número de frames PCM lógicos, no bytes de FLAC comprimido) y coexista junto a `0x0206`, ya que un lector que no entienda `0x0206` necesitaría de todos modos poder leer la metadata básica de audio desde `0x01f7`. Esto se marca explícitamente 🟡 **HIPÓTESIS DE DISEÑO**, no como hecho verificado.

## 14. Relación frameCount ↔ audio

Dos frameCounts distintos deben distinguirse explícitamente en el modelo (`DwpZone.audio`, §7.3) para no confundirlos, exactamente el tipo de error que causó `BUG-02` (confundir dos campos con nombres similares):

- **frameCount lógico**: número de frames PCM reales del audio (lo que `0x01f7` ya reporta hoy, `🟢 CONFIRMED`).
- **tamaño físico del payload FLAC**: bytes del stream comprimido embebido en `0x0206` — **no** es una función lineal simple del frameCount lógico (depende de la compresión), por lo que nunca debe derivarse ni compararse directamente contra frameCount sin pasar por el decoder FLAC. Ver §26.

## 15. PCM ↔ FLAC

🔴 **NO CONFIRMADO** el conjunto exacto de combinaciones canal/profundidad/sample-rate que DirectWave acepta embebidas. Lo único con evidencia indirecta (vía `0x01f7`, aplicable a External DWP, no verificado para Monolithic) es que el fixture real usa 32-bit float estéreo a 44.1kHz. `WavDecoder` ya soporta 8/16/24/32-bit PCM + float32 + EXTENSIBLE (`Doc/07_AUDIO_PIPELINE_AUDIT.md`), por lo que `PcmNormalizer` **puede** recibir cualquiera de esos formatos como entrada, pero la especificación no puede afirmar cuáles de ellos FLAC-embebido-en-DWP acepta sin evidencia real — se documenta como punto de validación obligatorio en la fase de implementación (ver §30, "malformed/multi-format tests").

## 16. FLAC STREAMINFO

🔴 **NO CONFIRMADO** contra este proyecto. La estructura pública de STREAMINFO (36 bytes: min/max block size, min/max frame size, sample rate, canales, bits por muestra, total de samples, MD5) es conocimiento del formato FLAC estándar, no evidencia específica de DirectWave — se trata explícitamente como una entrada externa que `FlacEncoder` deberá producir correctamente según la especificación pública de FLAC, sin que este documento la reinvente ni la de por válida para DirectWave sin verificación cruzada posterior (§32).

## 17. FLAC metadata

🔴 **NO CONFIRMADO** si DirectWave requiere, tolera o rechaza bloques METADATA adicionales más allá de STREAMINFO (SEEKTABLE, VORBIS_COMMENT, PADDING, etc.) dentro de un `0x0206` embebido. Regla de implementación futura (de menor riesgo, no evidencia): `FlacEncoder` debe producir el mínimo viable (solo STREAMINFO) en la primera iteración, y solo añadir bloques adicionales si un fixture monolítico real de referencia confirma que son necesarios o beneficiosos.

## 18. FLAC audio frames

🔴 **NO CONFIRMADO** ningún parámetro de codificación esperado (orden de predicción, tamaño de bloque, nivel de compresión). Tratado como decisión de implementación libre en la primera iteración (favoreciendo corrección/round-trip exacto sobre tamaño de archivo), sujeta a revisión si un fixture real muestra parámetros específicos.

## 19. Padding

🔴 **NO CONFIRMADO** si el payload de `0x0206` requiere padding de alineación (p. ej. a palabra de 4 bytes), a diferencia de `0x0004`/otros bloques que no lo necesitan porque el modelo DWP no tiene ninguna regla de alineación conocida (`Doc/06_BINARY_FORMAT_MODEL.md`: "No existen offsets absolutos... todo es posicional/secuencial"). Regla de implementación futura: no añadir padding por defecto (consistente con el modelo físico ya confirmado, que no lo requiere en ningún bloque conocido) salvo que la evidencia de un fixture real lo exija.

## 20. MD5

🔴 **NO CONFIRMADO** si DirectWave valida el MD5 de STREAMINFO al leer un monolítico. Se documenta como campo que `FlacEncoder` debe calcular correctamente de todos modos (parte de un FLAC válido según la especificación pública), independientemente de si DirectWave lo usa.

## 21. CRC

🔴 **NO CONFIRMADO**, mismo razonamiento que MD5 — FLAC exige CRC-8 (header de frame) y CRC-16 (frame completo) por especificación pública; `FlacEncoder` debe producirlos correctamente para tener un FLAC válido, independientemente de si DirectWave los revalida.

## 22. Canales

🟢 **CONFIRMADO** para External DWP (offset 8 de `0x01f7`, coincide con `WavDecoder.channelCount`). 🔴 **NO CONFIRMADO** para Monolithic si hay alguna restricción adicional (p. ej. solo mono/estéreo, no multicanal) — el único fixture real es estéreo.

## 23. Sample rate

🟢 **CONFIRMADO** para External DWP (offset 16 de `0x01f7`, float32, coincide exacto). 🔴 **NO CONFIRMADO** para Monolithic si el sample rate se almacena de la misma forma o se delega enteramente en STREAMINFO de FLAC (que también lo codifica).

## 24. Bits por muestra

🟢 **CONFIRMADO** para External DWP (offset 36 de `0x01f7`). 🔴 **NO CONFIRMADO** para Monolithic — y explícitamente relevante para `RISK-02`/`R-02` (`Doc/13_RISK_REGISTER.md`), que ya señala que ni siquiera el caso External de 16-bit PCM está verificado por falta de fixture. La implementación futura hereda esta misma limitación: no hay evidencia de ningún bit-depth salvo 32-bit float.

## 25. Guard frames

🔴 **NO CONFIRMADO.** No hay ninguna mención ni evidencia de "guard frames" en ningún documento existente ni en el fixture real (que no tiene loop activado en ninguna muestra, `Doc/05_DWP_IMPLEMENTATION_AUDIT.md` E-06). Se incluye en esta especificación porque el prompt de trabajo original lo menciona como concepto a documentar, pero se declara explícitamente sin ninguna evidencia — no se inventa su propósito ni su tamaño.

## 26. Tamaño físico vs tamaño lógico

Distinción obligatoria en el diseño (ya introducida en §14): el **tamaño lógico** es el frameCount PCM real; el **tamaño físico** es el tamaño en bytes del payload FLAC comprimido embebido en `0x0206`. `DwpAudioValidator` (§7.2) debe verificar la relación correcta entre ambos **decodificando** el FLAC de vuelta a PCM y comparando frame counts — nunca asumiendo una fórmula de compresión fija, porque FLAC es de tasa de compresión variable por diseño.

## 27. Orden físico de bloques

🔴 **NO CONFIRMADO** dónde debe insertarse `0x0206` dentro del payload de un `0x0003` en relación a los demás bloques hijos (`0x01f4`...`0x0204`, `0x0004`). Regla de implementación futura, no evidencia: dado que el modelo físico confirma que **no existen offsets absolutos** (`Doc/06_BINARY_FORMAT_MODEL.md`) y que el orden es puramente secuencial sin punteros que reparar, la posición de inserción **no debería** ser estructuralmente significativa — pero esto en sí es una inferencia sobre el modelo genérico, no una confirmación específica de que DirectWave tolere cualquier orden para `0x0206` en particular. La implementación futura debe insertarlo de forma determinista (p. ej. inmediatamente antes de `0x0004`) y tratar cualquier sensibilidad al orden descubierta contra un fixture real como una corrección de esta regla, documentada como tal.

## 28. Preservación de bloques no modificados

Hereda directamente la política ya implementada y verificada para `renameInstrument`/`replaceSampleAudio` (`Doc/04_EXECUTION_FLOW.md`, `Doc/18_CANONICAL_STATE.md` §A "Preservation"): todo bloque no tocado se copia byte a byte, incluidos duplicados de tag. `MonolithicZoneBuilder` debe seguir exactamente el mismo patrón: tokenizar el payload del `0x0003` con `tokenizeStrict` (nunca la variante permisiva, por la misma razón que ya motivó `RISK-05`/Fase 1), modificar/insertar solo el o los bloques relacionados con audio, y dejar `0x01f4`, `0x01f5`, `0x01f6`, y todo bloque `0x01f8`-`0x0204` **intactos**, sin reinterpretarlos ni "limpiarlos" (Sección 6 del prompt de esta fase).

## 29. Manejo de errores

Hereda el patrón ya establecido en el Core: fallar explícito con una excepción descriptiva (`DwpFormatException` para lo estructural, una futura `FlacEncodingException` dedicada para errores propios del encoder) en vez de producir un resultado silenciosamente incompleto o corrupto — el mismo principio que llevó a introducir `tokenizeStrict` y `StopReason` en el Core. Ningún componente nuevo debe usar el patrón "atrapar y devolver null/vacío" para un error de codificación o validación.

## 30. Límites y validaciones

Como mínimo, antes de escribir el `.dwp` resultante a disco:
- `DwpStructureValidator`: round-trip físico completo, `endOffset` exacto en cada nivel (top y cada `0x0003` anidado).
- `DwpAudioValidator`: frameCount lógico declarado coincide con el frameCount real tras decodificar el FLAC embebido.
- `FlacValidator`: round-trip PCM→FLAC→PCM bit-exacto (o, si la implementación es lossy en algún punto de normalización — no debería serlo, FLAC es lossless por diseño — documentado explícitamente por qué).
- Límite de tamaño razonable por sample embebido, en la misma línea que los límites ya existentes en `ZipProjectLoader` (`MAX_ENTRIES`/`MAX_SINGLE_ENTRY_BYTES`/`MAX_TOTAL_UNCOMPRESSED_BYTES`) — valor exacto a definir en la fase de implementación, no en esta especificación.

## 31. Compatibilidad esperada

🔴 **NO CONFIRMADO.** No hay ninguna base para afirmar que un `.dwp` Monolithic generado por esta futura implementación será leído correctamente por FL Studio Mobile/Desktop sin haberlo probado contra la aplicación real. Ver §36, criterio de aceptación explícito de "prueba real en FL Studio".

## 32. Riesgos

| ID | Riesgo | Heredado de | Estado |
|---|---|---|---|
| R-03 | `0x0206` hipotético sin evidencia real; implementarlo sin un fixture monolítico de referencia sería inventar estructura | `Doc/13_RISK_REGISTER.md` | **ABIERTO**, bloqueante para implementación real, no para esta especificación |
| R-08 | Sesgo de fixture único (`Instrument.dwp`, External DWP, sin variación interna) | `Doc/13_RISK_REGISTER.md` | **ABIERTO**, mismo bloqueo |
| R-02/RISK-02 | Offsets de `0x01f7` no verificados para 16-bit PCM, ni para ningún caso Monolithic | `Doc/13_RISK_REGISTER.md` | **ABIERTO** |
| R-NUEVO-01 | `FlacEncoder` es el componente de mayor riesgo técnico del proyecto: un encoder FLAC completo y correcto (predicción, Rice coding, CRC, MD5) es no trivial y su corrección debe verificarse con round-trip exacto, no solo "el archivo se abre" | Nuevo, identificado en esta fase | Riesgo de diseño a mitigar con `FlacValidator` (§7.2) antes de integrar con DWP |
| R-NUEVO-02 | Sin ningún `.dwp` monolítico real de referencia, cualquier implementación de `0x0206` es una hipótesis no verificable hasta obtener uno | Nuevo, refuerza R-03 | Bloqueante para pasar de "implementado" a "verificado" |

## 33. Puntos todavía no confirmados (resumen consolidado)

Lista agregada de todo lo marcado 🔴 en este documento, para referencia rápida de la fase de implementación: estructura de `0x0205`, estructura de `0x0206`, relación exacta `0x0206`↔`0x01f7`↔`0x0004`, profundidades de bits aceptadas embebidas, necesidad de bloques METADATA FLAC adicionales, parámetros de codificación FLAC esperados, necesidad de padding en `0x0206`, sensibilidad al orden de bloques dentro de un `0x0003` monolítico, compatibilidad real con FL Studio. Ninguno de estos puntos se resuelve inventando una respuesta; todos requieren un fixture monolítico real de referencia (misma conclusión que `Doc/08_MONOLITHIC_DWP_ANALYSIS.md` ya alcanzaba).

## 34. Estrategia de pruebas

Ver desglose completo en la Sección 11 original de este prompt, aplicada aquí como plan (no ejecutado):

- **Unit tests** por componente nuevo (`PcmNormalizer`, `DwpAudioBuilder`, `MonolithicZoneBuilder`) de forma aislada, con entradas sintéticas construidas a mano — mismo patrón ya usado en `WavDecoderTest`/`PcmConverterTest`.
- **Binary serialization tests**: verificar que `MonolithicZoneBuilder` produce exactamente los bytes esperados para casos construidos a mano (no solo "no lanza excepción").
- **FLAC round-trip tests**: PCM→FLAC→PCM bit-exacto, para mono/estéreo, para varias profundidades de bits soportadas por `WavDecoder` hoy (8/16/24/32-bit PCM, float32).
- **DWP round-trip tests**: construir un `.dwp` Monolithic sintético (partiendo del fixture real como plantilla, insertando un `0x0206` de prueba), reparsearlo, y confirmar que el modelo reconstruido es idéntico.
- **Malformed input tests**: FLAC corrupto, `0x0206` truncado, frameCount inconsistente entre `0x01f7` y el FLAC real.
- **Overflow/truncation tests**: mismo patrón que ya existe en `DwpTokenizer`/`WavDecoder` (aritmética en `Long`, rechazo explícito).
- **Frame-count / channel / sample-rate / bit-depth tests**: para cada combinación soportada por `WavDecoder`.
- **Multi-sample / mono / stereo tests**: sobre un documento con más de un `0x0003`, confirmando que solo los samples objetivo cambian (mismo patrón que `patchFrameCount updates only the targeted sample`, ya existente).
- **Regresión contra `Instrument.dwp`**: confirmar que el pipeline External DWP existente (parse/rename/replaceSampleAudio/export) sigue produciendo bytes idénticos a los actuales cuando no se usa ninguna ruta Monolithic — el fixture real no debe verse afectado por la existencia de código nuevo no invocado.

## 35. Estrategia de regresión

Antes de dar por buena cualquier implementación futura: (a) los 125 tests actuales deben seguir pasando sin modificación de su contenido (solo se permite añadir tests nuevos, nunca alterar el comportamiento esperado de los existentes salvo que se documente por qué, mismo patrón ya seguido en Fase 2 con los 2 tests corregidos por `BUG-02`); (b) el SHA-256 del fixture real debe permanecer sin cambios salvo que una operación lo modifique intencionalmente (mismo patrón de verificación ya usado en `Doc/22` §12); (c) ninguna función pública existente de `DwpEngine`/`WavDecoder`/`ZipProjectLoader`/`ZipProjectExporter` cambia de firma de forma incompatible.

## 36. Criterio de aceptación

La futura implementación de Monolithic DWP + FLAC podrá considerarse válida cuando, como mínimo:

- El `.dwp` generado es estructuralmente válido: `DwpStructureValidator` confirma round-trip exacto en todos los niveles.
- El audio FLAC embebido es válido: se decodifica sin error y `FlacValidator` confirma round-trip PCM exacto.
- Los tamaños son coherentes: `DwpAudioValidator` confirma frameCount lógico ↔ audio real decodificado.
- `frameCount` es coherente en todos los bloques relacionados (`0x01f7` si se mantiene, STREAMINFO de FLAC).
- La metadata es coherente (canales/sampleRate/bits consistentes entre `0x01f7` si se mantiene y STREAMINFO).
- El EOF es correcto: el archivo termina exactamente donde el último bloque serializado indica, sin sobrante ni déficit (mismo test que ya existe para el fixture real, aplicado al nuevo output).
- No hay corrupción de bloques no relacionados con audio (verificado byte a byte contra la plantilla original, para todo lo no tocado).
- Los tests nuevos de la Sección 34 pasan.
- CI pasa (`gradle testDebugUnitTest` + `gradle assembleDebug`, mismo pipeline ya verificado en `Doc/22`).
- Compatibilidad comprobada contra al menos un `.dwp` Monolithic real de referencia obtenido externamente (condición para poder cerrar R-03/R-08, no alcanzable sin ese fixture).
- Prueba real en FL Studio (Desktop y/o Mobile, según corresponda) abriendo el `.dwp` generado y confirmando reproducción de audio correcta — la misma disciplina de "no declarar éxito sin evidencia real de ejecución" que ya se aplicó a los 125 tests del Core (`Doc/22_CORE_STABILIZATION_FINAL_VERIFICATION.md`).

---

## Reporte final de esta fase

**A. Documentos leídos:** los 23 documentos existentes de `Doc/` (listado completo en §A.1), más lectura directa de `DwpBlock.kt`, `DwpVersionProfile.kt`, `DwpTokenizer.kt`, `DwpDocument.kt` para verificar nombres/tags exactos antes de citarlos.

**B. Documentos modificados:** ninguno.

**C. Documento creado:** `Doc/23_MONOLITHIC_DWP_FLAC_IMPLEMENTATION_SPEC.md` (este archivo).

**D. Decisiones heredadas de investigaciones anteriores:** modelo físico de bloques (tag/length/reserved/payload, sin offsets absolutos), tags top-level y anidados confirmados, orden root/low/high de `0x01f4`, offsets confirmados de `0x01f7`, ausencia total de `0x0205`/`0x0206` en el único fixture real, punto de inserción arquitectónico ya identificado en `ZipProjectExporter`/`DwpEngine.replaceSampleAudio`, política de preservación binaria ya implementada para rename/replace.

**E. Decisiones nuevas de esta fase:** el desglose de componentes de §7.2 con responsabilidades únicas explícitas (`PcmNormalizer`, `FlacEncoder`, `DwpAudioBuilder`, `MonolithicZoneBuilder`, `GlobalTemplateBuilder`, los 4 validadores, `DwpBuilder`); el modelo `DwpZone`/`ZoneAudio` sellado de §7.3; la hipótesis de diseño (marcada 🟡, no confirmada) de que `0x01f7` coexiste con `0x0206` en vez de ser reemplazado.

**F. Elementos confirmados (🟢):** magic/preámbulo, modelo físico de bloques, tags top-level y anidados ya conocidos, orden root/low/high de `0x01f4`, offsets 0/8/16/36 de `0x01f7` (32-bit float), `0x0004` como terminador, ausencia de `0x0205`/`0x0206` en el fixture real, los 125 tests y su ejecución real en CI (heredado, no reverificado en esta fase).

**G. Elementos inferidos (🟡):** offset 12 de `0x01f7` (bytes por muestra), estructura de `0x0204` (16 slots × 8 bytes), coexistencia hipotética `0x01f7`↔`0x0206`.

**H. Elementos todavía desconocidos (🔴):** estructura de `0x0205`, estructura de `0x0206`, todo el pipeline PCM↔FLAC específico de DirectWave (STREAMINFO/metadata/frames/padding/MD5/CRC en el contexto DWP), bit-depths embebidos soportados, orden de bloques requerido, guard frames, compatibilidad real con FL Studio — listado completo en §33.

**I. Arquitectura propuesta:** ver §7 completa.

**J. Contratos propuestos:** entradas/salidas de cada componente nuevo, tabla de §7.2.

**K. Plan de implementación:** flujo de §5, orden de componentes de §7.2, dependencia explícita en obtener un fixture monolítico real antes de poder cerrar R-03/R-08 (§32).

**L. Plan de tests:** §34.

**M. Riesgos:** §32, con dos riesgos nuevos identificados en esta fase (`R-NUEVO-01`, `R-NUEVO-02`) además de los tres ya heredados (R-02/RISK-02, R-03, R-08).

**N. Confirmación explícita:**

**NO se implementó Monolithic DWP/FLAC en esta fase.** No se escribió ningún encoder, ningún writer de `0x0205`/`0x0206`, ningún validador, y no se modificó ningún archivo de producción (`app/src/main`) ni de test (`app/src/test`). El único artefacto producido es este documento de especificación.
