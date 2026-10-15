# 16 — FASE 1: FUNDACIÓN BINARIA (cambios implementados)

Este documento registra los cambios de código realizados **después** del cierre de la Fase 0, siguiendo el plan propuesto en `15_AUDIT_CONCLUSIONS.md` § "Plan de fases". Alcance deliberadamente mínimo: solo se tocaron los dos hallazgos de menor riesgo y mayor claridad de la auditoría. No se ha tocado nada relacionado con FLAC, `0x0206`, ni ningún modelo semántico — eso sigue bloqueado por falta de evidencia (ver `08_MONOLITHIC_DWP_ANALYSIS.md`).

## Cambio 1 — Cierre de RISK-05 (fidelidad binaria en payloads anidados)

**Problema (evidencia original):** `DwpDocument.parse` ya verificaba que la tokenización top-level consumiera exactamente todo el archivo, pero `DwpEngine.renameRecursive`, `patchFrameCount` y `replaceSampleAudio` tokenizaban el payload anidado de cada `0x0003` con la variante permisiva (`DwpTokenizer.tokenize`), sin comprobar que el cursor llegara al final del payload. Si algún `.dwp` (de otra versión/variante no contemplada) tuviera un contenedor de sample con bytes finales no interpretables como bloque, esas funciones habrían reserializado una copia truncada, perdiendo esos bytes en silencio.

**Qué se hizo:**
- Se añadió `DwpTokenizer.tokenizeStrict(buf, startOffset, context)`: igual que `tokenize`, pero lanza `DwpFormatException` (mensaje con nombre de contexto y cantidad exacta de bytes en riesgo) si el cursor final no coincide con el tamaño del buffer.
- `renameRecursive`, `patchFrameCount` y `replaceSampleAudio` ahora usan `tokenizeStrict` para el payload anidado — **cualquier edición sobre un contenedor de sample con formato inesperado ahora falla ruidosamente en vez de corromper datos silenciosamente.**
- `listSamples` **deliberadamente sigue usando la variante permisiva** `tokenize`, con un comentario explicando por qué: es una operación de solo lectura que nunca reserializa, así que no puede perder bytes; forzarla a fallar duro degradaría la UX de solo mostrar una lista por un problema en un único sample.

**Compatibilidad:** cero cambios de comportamiento sobre el archivo de referencia real (`Instrument.dwp`) — ya se había verificado en la Fase 0 que su tokenización anidada no deja bytes sin cubrir (0 mismatches en los 48 samples). El cambio solo afecta al comportamiento ante un `.dwp` que NO cumpla el formato verificado, que antes se editaba a ciegas y ahora se rechaza explícitamente.

**Tests añadidos** (`DwpEngineTest.kt`):
- `tokenizeStrict throws instead of silently dropping unconsumed trailing bytes` — prueba unitaria directa sobre `DwpTokenizer`, sin depender del fixture completo.
- `renameInstrument refuses to edit a document with a corrupted sample container` — construye un documento derivado del fixture real con 5 bytes de basura añadidos al final de un contenedor de sample, y confirma que ahora se lanza `DwpFormatException` en vez de reserializar una copia truncada.
- `patchFrameCount refuses to edit a corrupted sample container rather than lose bytes` — mismo caso, para la segunda función afectada.
- `listSamples still reads a corrupted sample container leniently` — confirma que la operación de solo lectura sigue funcionando (degradación correcta, no regresión de UX).

**Archivos modificados:**
- `app/src/main/java/com/jvk/dwpcreator/domain/dwp/DwpTokenizer.kt`
- `app/src/main/java/com/jvk/dwpcreator/domain/dwp/DwpEngine.kt`
- `app/src/test/java/com/jvk/dwpcreator/domain/dwp/DwpEngineTest.kt`

## Cambio 2 — Mitigación de BUG-01 (UI "fantasma": Renombrar/Eliminar individual)

**Problema (evidencia original):** `SampleRow` mostraba en su menú contextual las opciones "Renombrar" y "Eliminar" por sample, cableadas hasta `MainScreen.onSampleRename`/`onSampleDelete`, pero `MainActivity` nunca las conectaba a ninguna implementación real — quedaban en su valor por defecto `{}`, no-ops silenciosos.

**Qué se hizo (mitigación de bajo riesgo, NO implementación completa):** se retiraron las opciones "Renombrar" y "Eliminar" del menú contextual de `SampleRow`, dejando solo "Preview" (la única acción que sí funciona end-to-end). Se retiraron también los parámetros `onSampleRename`/`onSampleDelete` de `MainScreen` y su wiring hacia `SampleRow`, ya que no tenían ningún consumidor real.

**Por qué NO se implementó la funcionalidad completa en esta fase:** un renombrado o borrado real de un sample individual requiere, además de la operación en `DwpEngine` (que hoy no existe — solo existe el renombrado global `renameInstrument`), **reindexar `LoadedProject.audioByIndex`**, que hoy está emparejado estrictamente por posición con `DwpDocument.blocks`. Borrar un sample cambiaría esa correspondencia y afecta también a `ZipProjectExporter` (que itera samples y audio en paralelo asumiendo la misma longitud y orden). Es un cambio de mayor alcance que merece su propio diseño y sus propios tests de round-trip, no una corrección apresurada dentro de la fundación binaria. Queda registrado como trabajo pendiente explícito, no como "ya resuelto".

**Compatibilidad:** no se pierde ninguna funcionalidad real (las opciones retiradas nunca funcionaron). El comportamiento de "Preview" (tap y menú contextual) es idéntico a antes.

**Archivos modificados:**
- `app/src/main/java/com/jvk/dwpcreator/ui/components/SampleRow.kt`
- `app/src/main/java/com/jvk/dwpcreator/ui/screens/MainScreen.kt`

## Estado de la matriz de problemas tras esta fase

| ID | Estado anterior | Estado actual |
|---|---|---|
| RISK-05 | ABIERTO | **CERRADO** — verificación estricta añadida + tests de regresión |
| BUG-01 | ABIERTO (HIGH) | **MITIGADO** (UI ya no miente sobre su capacidad); implementación completa de rename/delete individual sigue pendiente y se reclasifica como trabajo de una fase futura, no como bug de esta fase |

Los demás hallazgos de `12_KNOWN_ISSUES.md` y `13_RISK_REGISTER.md` (RISK-01, RISK-02, RISK-03, RISK-04, RISK-06, INFO-01, INFO-02, UX-01) **siguen abiertos** — no se tocaron en esta fase por estar fuera de su alcance mínimo declarado.

## Verificación

**No se pudo ejecutar `gradle testDebugUnitTest` en este entorno** (sin red / sin Android SDK, misma limitación ya declarada en `10_BUILD_AUDIT.md`). La corrección se verificó por:
1. Lectura completa de cada archivo modificado tras la edición, confirmando consistencia de tipos, imports y llaves.
2. Razonamiento manual del nuevo test contra la lógica exacta de `tokenizeStrict`/`tokenize` ya verificada en la Fase 0.
3. Búsqueda global (`grep`) confirmando que no quedan referencias colgantes a los parámetros/callbacks retirados.

**Recomendación para el usuario:** al compilar vía GitHub Actions (según el flujo de trabajo descrito en la Sección 65 del prompt maestro), confirmar que `gradle testDebugUnitTest` pasa con los 4 tests nuevos antes de continuar con la Fase 2.
