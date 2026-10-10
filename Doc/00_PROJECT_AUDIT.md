# 00 — AUDITORÍA INTEGRAL DEL PROYECTO (Resumen Ejecutivo)

**Fecha de auditoría:** 2026-09-07 (Fase 0) — **actualizado tras auditoría de la auditoría, ver `17_AUDIT_ERRATA.md` y `18_CANONICAL_STATE.md` para el estado corregido y vigente.**
**Método:** Lectura completa del código fuente real (**33 archivos Kotlin** — cifra corregida, conteo original decía 30; ~3.100 líneas), los 4 archivos de test (todos ejecutan contra un fixture binario real), y verificación binaria independiente del archivo `app/src/test/resources/Instrument.dwp` (47.307 bytes) mediante un tokenizer Python escrito ex-profeso para esta auditoría, replicando byte a byte la lógica de `DwpTokenizer.kt` sin reutilizar su código.
**Alcance NO cubierto:** no se pudo compilar el proyecto (sandbox sin red / sin Android SDK) ni ejecutar `gradle testDebugUnitTest` realmente — ver `10_BUILD_AUDIT.md` para el detalle de esta limitación y su clasificación como `[UNKNOWN]`.

---

## 1. Qué es DwpCreator realmente (evidencia, no README)

`[CODE]` Es una app Android nativa (Kotlin + Jetpack Compose, sin JNI/NDK/C/C++) cuya función real, ya implementada y cubierta por tests que corren contra un `.dwp` real, es:

1. Cargar un `.zip` que contiene un `.dwp` de DirectWave (exportado desde FL Studio Desktop) + sus `.wav` asociados.
2. Parsear el `.dwp` en un modelo binario fiel (tag/length/reserved/payload).
3. Renombrar el instrumento completo (nombre + rutas + nombre/ruta de cada sample) de forma segura, sin tocar bloques binarios.
4. Reproducir cada sample de forma nativa (AudioTrack) y responder a MIDI externo.
5. Re-exportar un `.zip` (`.dwp` + `.wav`) listo para FL Studio Mobile, recalculando la metadata de formato de audio (`0x01f7`) de cada muestra a partir del `.wav` real que se va a escribir.

`[CODE]` Esto **contradice directamente al `README.md`**, que describe el proyecto como "Paso 1 de 7: esqueleto del proyecto" sin motor DWP ni de audio. El propio `MainActivity.kt` dice en un comentario interno: *"Paso 7 complete: the full pipeline"*. El README está desactualizado — ver `11_DOCUMENTATION_AUDIT.md`.

`[CODE]` Lo que el proyecto **NO hace todavía**, confirmado por ausencia total de código relacionado:
- No genera `.dwp` **monolíticos** (con audio embebido). El modelo actual es 100% "External DWP": el `.dwp` solo referencia nombres de archivo `.wav` externos; el propio `ZipProjectExporter` escribe el `.wav` como archivo hermano, nunca dentro del `.dwp`.
- No existe ningún encoder/decoder FLAC, ni el tag `0x0206`, ni ninguna mención a STREAMINFO/MD5/Rice coding en el código real (sí se mencionan como objetivo futuro en el propio prompt de trabajo, no en el código).
- No existe manipulación de filtros, envolventes, LFO ni matriz de modulación — el motor solo toca 4 tags: `0x0066` (nombre), `0x0067` (ruta del propio dwp), `0x01f5`/`0x01f6` (nombre/ruta de sample) y `0x01f7` (formato de audio).

## 2. Problemas críticos encontrados (detalle en `12_KNOWN_ISSUES.md` y `13_RISK_REGISTER.md`)

| ID | Severidad | Resumen |
|---|---|---|
| BUG-01 | HIGH | El menú contextual de cada sample expone "Renombrar" y "Eliminar" individuales, pero `MainActivity.kt` no conecta `onSampleRename`/`onSampleDelete` a nada — son no-ops silenciosos. Funcionalidad visible pero inexistente. |
| RISK-01 | MEDIUM | `DwpEngine.renameInstrument` reemplaza el nombre antiguo en **cualquier** bloque que decodifique como texto imprimible completo, sin importar el tag. En el archivo de referencia no causa daño (los campos opacos son ceros no imprimibles), pero es un riesgo estructural: un `.dwp` con metadata textual coincidente por casualidad sufriría una sustitución no intencionada. |
| RISK-02 | MEDIUM | `replaceSampleAudio` documenta explícitamente (comentario del propio autor) que el mapeo de offsets del blob `0x01f7` de 40 bytes solo está verificado contra un `.wav` de referencia en 32-bit float; no hay evidencia para PCM de 16 bits. |
| RISK-03 | LOW | Copia UI (`LoadEmptyState`) anuncia soporte para cargar un `.dwp` suelto ("Formatos soportados: .zip (dwp + wav), .dwp"), pero `ZipProjectLoader.load` solo sabe leer un `.zip`; un `.dwp` suelto fallaría. |
| INFO-01 | INFO | No existe `gradlew`/`gradle-wrapper.jar` en el repo. El CI (`build.yml`) asume un `gradle` de sistema (`gradle-version: 8.6` vía `gradle/actions/setup-gradle`), lo cual funciona en GitHub Actions pero impide build reproducible fuera de ese workflow sin instalar Gradle manualmente. |
| BUG-02 | HIGH | `DwpEngine.listSamples()` intercambia las etiquetas `lowKey`/`rootKey` del bloque `0x01f4` (el orden físico real es root/low/high, no low/root/high). Consecuencia confirmada: `DwpCreatorViewModel` usa `it.lowKey..it.highKey` para decidir qué sample suena ante una nota MIDI — con las etiquetas cambiadas, las notas MIDI 0-35 no disparan ningún sample en este instrumento. Encontrado en la auditoría de la auditoría, no en la Fase 0 original — ver `17_AUDIT_ERRATA.md`. |

## 3. Componentes reutilizables (clasificación completa en `14_KEEP_REFACTOR_REPLACE.md`)

`[CODE]`+`[BINARY]` **KEEP, con alta confianza:**
- `DwpBlock` / `DwpTokenizer` / `DwpDocument`: el modelo físico (tag/length/reserved/payload) fue verificado byte a byte contra el archivo real — 0 bytes de sobrante, 0 bytes perdidos, duplicados de tag preservados. Es una base sólida para la reingeniería hacia DWP monolítico.
- `WavDecoder`: cubre PCM 8/16/24/32-bit + float32 + WAVE_FORMAT_EXTENSIBLE, con tests unitarios reales por cada rama.
- El modelo de lista plana `List<DwpBlock>` (no `Map<Tag, Block>`) ya satisface el requisito de la Sección 12 del prompt de trabajo (preservar duplicados y orden) — **no hace falta reemplazar esta parte**, es una base correcta.

## 4. Incógnitas abiertas (detalle en `15_AUDIT_CONCLUSIONS.md` y conocimiento DWP)

`[UNKNOWN]` Todo lo relacionado con FLAC, `0x0206`, filtros, envolventes, LFO y matriz de modulación: **no hay ninguna evidencia en este proyecto** (ni código, ni tests, ni comentarios) sobre estos temas. Cualquier afirmación al respecto deberá venir de una fuente externa (ChatGPT/reverse engineering) y documentarse por separado, nunca mezclada con lo verificado aquí.

## 5. Documentos creados

Ver lista completa y confirmación de existencia real en disco al final de este documento y en la respuesta de cierre de la Fase 0.
