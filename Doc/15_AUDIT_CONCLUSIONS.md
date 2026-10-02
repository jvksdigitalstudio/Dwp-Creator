# 15 — CONCLUSIONES DE LA AUDITORÍA Y PROPUESTA DE REINGENIERÍA

> **Nota de vigencia:** este documento cerró la Fase 0 originalmente. Contenía dos errores corregidos aquí (ver `17_AUDIT_ERRATA.md` E-10/E-11). Para el estado agregado más actual, ver siempre `18_CANONICAL_STATE.md` primero.

Este documento cierra la Fase 0. No se ha implementado nada de lo aquí propuesto — es diseño para revisión antes de comenzar la Fase 1.

## Resumen del estado real (una frase por área, cada una respaldada por evidencia en los documentos anteriores)

- **Qué es hoy:** una app Android funcional que edita y reempaqueta DWP "External" (metadata + `.wav` externos), no genera DWP monolítico.
- **Qué tan confiable es el núcleo binario:** alto — verificado con round-trip binario exacto contra un archivo real, incluyendo preservación de tags duplicados.
- **Qué tan confiable es el pipeline de audio:** alto para lectura/decodificación WAV; no aplica todavía a audio embebido (no existe).
- **Qué tan confiable es la UI:** funcional salvo `BUG-01` (mitigado en Fase 1 — opciones no funcionales retiradas del menú, no implementadas), `BUG-02` (etiquetas de key range intercambiadas, con consecuencia funcional real en el disparo MIDI — ver `12_KNOWN_ISSUES.md`), y un caso de copy engañoso (RISK-03).
- **Qué tan confiable es el build:** configurado correctamente para CI; no verificable de forma independiente en este entorno de auditoría (sin red/SDK); sin wrapper para desarrollo local.
- **Qué falta para el objetivo final (DWP monolítico + FLAC):** todo el conocimiento del formato de audio embebido y el encoder FLAC — cero evidencia existente, es investigación nueva, no extensión de código existente.

## Registro de conocimiento DWP (clasificación acumulada de todos los documentos)

| Campo/estructura | Estado |
|---|---|
| Magic `"DwPr"`, preámbulo de 90 bytes como bloque opaco | **CONFIRMED** |
| Formato de bloque tag/length/reserved/payload, sin offsets absolutos | **CONFIRMED** |
| Preservación de tags duplicados por lista (no mapa) | **CONFIRMED** |
| Tags top-level `0x0066`, `0x0067`, `0x006e`, `0x0003`, `0x0002` | **CONFIRMED** |
| Tags top-level `0x0068`-`0x006d` (metadata vacía) | **CONFIRMED presencia**, contenido siempre-cero en el único fixture — **HYPOTHESIS** sobre qué contendrían si no estuvieran vacíos |
| `0x01f4` key range: **root/low/high** en bytes 0/1/2 (corregido — la versión original decía low/root/high, ver `17_AUDIT_ERRATA.md` E-01) | **CONFIRMED** para esos 3 bytes (verificado sobre las 48 muestras); el resto de los 25 bytes (velocidad/flags) — **HYPOTHESIS** |
| `0x01f5`/`0x01f6` nombre/ruta de sample | **CONFIRMED** |
| `0x01f7` formato de audio, offsets 0/8/12/16/36 | **CONFIRMED** para 32-bit float; **HYPOTHESIS** para 16-bit PCM |
| `0x01f8`-`0x0204` (envolventes/loop/tuning) | **OPAQUE** — sin decodificar, tratado correctamente como tal por el código |
| `0x0206` (audio embebido FLAC hipotético) | **UNKNOWN** total — sin evidencia en este proyecto |
| Interior del preámbulo de 90 bytes | **UNKNOWN** — nunca decodificado, tratado como opaco |
| Modelo monolítico completo (DWP + FLAC embebido) | **UNKNOWN** total |

## Arquitectura objetivo propuesta (NO implementada en esta fase)

Basada en el punto de inserción real identificado en `08_MONOLITHIC_DWP_ANALYSIS.md`, extendiendo — no reemplazando — lo que ya existe:

```
DwpBuilder (nuevo, orquesta sobre DwpDocument/DwpTokenizer YA EXISTENTES)
 ├── GlobalTemplateBuilder      (usa DwpDocument.parse existente como plantilla base)
 ├── MonolithicZoneBuilder      (nuevo: construye/edita el payload de un 0x0003)
 ├── DwpAudioBuilder            (nuevo)
 │    ├── PcmNormalizer         (se apoya en WavDecoder/PcmConverter EXISTENTES)
 │    ├── FramePadder           (nuevo)
 │    ├── GuardAppender         (nuevo)
 │    └── FlacEncoder           (nuevo — el componente de mayor riesgo/incertidumbre)
 ├── Serializer                 (reutiliza DwpTokenizer.serialize EXISTENTE, sin cambios)
 └── Validators                 (nuevo, ver abajo)
```

Modelo físico futuro (extiende `DwpBlock`, no lo reemplaza):
```
DwpRecord
 ├── tag            (= DwpBlock.tag, ya existe)
 ├── payloadLength  (= DwpBlock.length, ya existe)
 ├── reserved       (= DwpBlock.reserved, ya existe)
 ├── payload        (= DwpBlock.payload, ya existe)
 └── children       (NUEVO: representación tipada de un payload ya tokenizado, opcional,
                      para no perder la representación cruda cuando no se necesita editar)
```

Modelo semántico futuro (nuevo, capa por encima del modelo físico, sin tocarlo):
```
DwpZone
 ├── Mapping            (deriva de 0x01f4 — YA se lee hoy en SampleInfo, formalizar como propio)
 ├── SampleIdentity      (deriva de 0x01f5/0x01f6 — ya se lee hoy)
 ├── Audio               (deriva de 0x01f7 hoy; se extenderá a 0x0206 cuando haya evidencia)
 ├── Filters             (UNKNOWN hoy — requiere investigación)
 ├── AmplitudeEnvelope   (UNKNOWN hoy)
 ├── ZoneEnvelopes       (UNKNOWN hoy)
 ├── ZoneLFOs            (UNKNOWN hoy)
 ├── ModulationMatrix    (UNKNOWN hoy)
 └── OpaqueTemplateData  (todo lo que hoy vive en 0x01f8-0x0204, preservado sin interpretar)
```

Validadores futuros (ninguno existe hoy):
```
DwpStructureValidator     — round-trip + endOffset por nivel (top Y anidado, cerrando RISK-05)
DwpPreambleValidator      — tamaño/magic (ya cubierto parcialmente por DwpDocument.parse)
DwpZoneValidator          — invariantes de 0x0003 (nº de campos esperados, límites de key range)
DwpAudioValidator         — coherencia frame count / tamaño real de audio
FlacValidator             — round-trip PCM→FLAC→PCM, sample count, MD5, CRC (Sección 55 del prompt maestro)
DwpConsistencyValidator   — validación final agregada antes de escribir a disco
```

## Plan de fases propuesto (copiado y adaptado del prompt de trabajo, sin iniciar ninguna)

1. **Fundación binaria** — ya prácticamente cubierta por `DwpTokenizer`/`DwpBlock` existentes; solo cerrar RISK-05 (verificación de `endOffset` anidado).
2. **Modelo físico DWP** — extender `DwpDocument`/`DwpBlock` hacia `DwpRecord` si se necesita la representación tipada de hijos; puede no ser necesario si el modelo actual basta.
3. **Modelo semántico** — introducir `DwpZone` y afines, como capa de lectura sobre lo ya existente, sin tocar `DwpEngine`.
4. **Templates** — formalizar el uso de un `.dwp` real como plantilla base (ya es, de facto, cómo funciona `ZipProjectLoader` hoy).
5. **Pipeline PCM** — reutilizar `WavDecoder` (ya cubre 8/16/24/32-bit + float32 + EXTENSIBLE); no requiere cambios para alimentar un futuro encoder FLAC.
6. **Encoder FLAC** — **el mayor riesgo del proyecto** (R-03); requiere especificación externa validada, no debe inventarse.
7. **Bloque `0x0206`** — bloqueado hasta tener al menos un `.dwp` monolítico real de referencia para repetir la metodología de verificación binaria de esta auditoría.
8. **Construcción de zonas** — sobre `MonolithicZoneBuilder`, construyendo el payload de `0x0003` con el nuevo bloque de audio embebido en lugar de (o junto a) `0x01f7`.
9. **Serialización** — sin cambios; `DwpTokenizer.serialize` ya sirve.
10. **Validadores** — construir los 6 listados arriba, empezando por `DwpStructureValidator` (el de menor riesgo y mayor reutilización de lo ya existente).
11. **Tests** — extender la disciplina ya presente (tests contra fixtures reales, no mocks) a cada nuevo componente.
12. **Primer DWP monolítico mínimo** — 1 zona, 512 frames lógicos, mono, 16-bit, 44.1kHz, FLAC, `0x0206` (Sección 54 del prompt maestro), validado en cascada (PCM→FLAC→PCM, luego parser propio, luego DirectWave real) antes de escalar complejidad.

## Confirmación de cierre de Fase 0

Todos los puntos de la Sección 61 del prompt de trabajo maestro fueron cubiertos:
1–8: inspección, auditoría de código/arquitectura/DWP/audio/tests/build, comparación documentación-vs-realidad, identificación de bugs y riesgos — todos con evidencia `[CODE]`/`[TEST]`/`[BINARY]` citada en los documentos correspondientes.
9: clasificación completa en `14_KEEP_REFACTOR_REPLACE.md`.
10–12: carpeta `Doc/` creada con los 16 documentos Markdown listados abajo.
13: conocimiento confirmado/desconocido documentado en este archivo (tabla de Registro de Conocimiento DWP).
14: arquitectura de reingeniería propuesta arriba, sin implementar.

## Lista de documentos creados en `Doc/`

```
Doc/00_PROJECT_AUDIT.md
Doc/01_PROJECT_STRUCTURE.md
Doc/02_REAL_ARCHITECTURE.md
Doc/03_CODE_INVENTORY.md
Doc/04_EXECUTION_FLOW.md
Doc/05_DWP_IMPLEMENTATION_AUDIT.md
Doc/06_BINARY_FORMAT_MODEL.md
Doc/07_AUDIO_PIPELINE_AUDIT.md
Doc/08_MONOLITHIC_DWP_ANALYSIS.md
Doc/09_TEST_AUDIT.md
Doc/10_BUILD_AUDIT.md
Doc/11_DOCUMENTATION_AUDIT.md
Doc/12_KNOWN_ISSUES.md
Doc/13_RISK_REGISTER.md
Doc/14_KEEP_REFACTOR_REPLACE.md
Doc/15_AUDIT_CONCLUSIONS.md
Doc/16_PHASE1_CHANGES.md          (posterior a esta lista original — cambios de Fase 1)
Doc/17_AUDIT_ERRATA.md            (posterior — auditoría de esta misma auditoría)
Doc/18_CANONICAL_STATE.md         (posterior — estado agregado más actual, empezar por aquí)
```

**No se inicia ninguna fase de implementación.** Esta auditoría queda a la espera de revisión antes de proceder con la Fase 1.
