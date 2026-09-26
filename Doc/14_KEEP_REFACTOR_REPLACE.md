# 14 — MATRIZ KEEP / REFACTOR / REPLACE / REMOVE

> **Actualizado en Fase 2** (`Doc/19_PHASE2_CORE_HARDENING.md`): los componentes que estaban en `REFACTOR` por problemas ya corregidos vuelven a `KEEP`, reflejando el estado real del código tras la corrección — no se re-escribe la clasificación anterior, se documenta explícitamente el cambio.

| Componente | Estado | Clasificación | Motivo |
|---|---|---|---|
| `DwpBlock` | Verificado contra binario real; KDoc de `0x01f4` corregido en Fase 2 | **KEEP** | Modelo físico exacto, sin pérdidas |
| `DwpTokenizer` | **Corregido en Fase 2**: `StopReason` estructurado + aritmética de límites segura contra overflow (era RISK-04) | **KEEP** (era "KEEP, refactor menor futuro" — el refactor ya se hizo) | — |
| `DwpDocument` | Correcto, estricto; mensaje de error mejorado en Fase 2 usando `StopReason` | **KEEP** | Ejemplo de buena práctica: no acepta silenciosamente un archivo que no cuadra |
| `DwpEngine` | **`BUG-02` corregido en Fase 2** (orden root/low/high); rename restringido a `RENAMABLE_TAGS` (era RISK-01); validación de índice añadida (era P-09) | **KEEP** (reclasificado desde REFACTOR de la pasada anterior — las 3 correcciones ya se aplicaron) | La corrección fue quirúrgica: 2 líneas para el bug de etiquetas, un allow-list para el rename, un helper de validación — sin tocar el mecanismo central (round-trip, tokenización) |
| `SampleInfo` | Trivial, correcto; ahora recibe datos correctamente etiquetados | **KEEP** | — |
| `WavDecoder` | **Endurecido en Fase 2**: validación `dataSize % bytesPerFrame`, protección de overflow, tamaño mínimo de `fmt ` | **KEEP** | Base sólida para cualquier futuro encoder FLAC |
| `PcmConverter` | Correcto para su único uso real (preview) | **KEEP** | No usado en el pipeline de exportación |
| `ZipProjectLoader` | **Endurecido en Fase 2**: rechaza múltiples `.dwp` y colisiones de nombre base de `.wav` (eran riesgos sin cerrar) | **KEEP** | — |
| `ZipProjectExporter` | **Endurecido en Fase 2**: sanitización de nombres contra path traversal, detección de nombres duplicados | **KEEP** | Punto de inserción natural para audio embebido futuro |
| `LoadedProject` | Trivial, correcto | **KEEP** | — |
| `SamplePlayer` | **Endurecido en Fase 2**: validación de `channelCount`, límite de voces concurrentes | **KEEP** (UX-01 de fallo silencioso ante WAV corrupto sigue sin tocar, fuera de alcance de esta fase) | — |
| `MidiInputManager` | **Endurecido en Fase 2**: soporte de running status añadido | **KEEP** | — |
| `DwpCreatorViewModel` | Correcto, concentra algo de lógica de negocio; se beneficia automáticamente de la corrección de `BUG-02` en `listSamples()` (línea 167 ya no requiere ningún cambio propio) | **KEEP** | Aceptable a esta escala |
| `MainActivity` | Bug de conexión de callbacks (BUG-01) | **REFACTOR** (sin cambios en Fase 2 — el menú roto ya se ocultó en Fase 1, la implementación real de rename/delete individual sigue pendiente y fuera de alcance de "Core") | Conectar o implementar `DwpEngine.renameSample`/`removeSample` en una fase futura |
| `MainScreen` | Correcto en sí mismo | **KEEP** | — |
| `SampleRow` | Ya mitigado en Fase 1 (opciones no funcionales retiradas) | **KEEP** | — |
| Resto de componentes UI (`DwpTopBar`, `ImportingOverlay`, `LoadEmptyState`, `MidiDevicesDialog`, `PianoKeyBadge`, `RenameAllDialog`, `StatusBar`) | Correctos, sin lógica de negocio (`LoadEmptyState` sigue con copy engañoso, RISK-03, sin tocar en esta fase) | **KEEP** | — |
| `DwpUiState` | Buen diseño (sealed class, sin estados imposibles) | **KEEP** | — |
| `PreviewData` | Uso interno de desarrollo (`@Preview`) | **KEEP** | No afecta producción |
| `DwpCreatorApplication` | Diagnóstico de crash, explícitamente temporal | **KEEP** (revisar antes de release) | — |
| `README.md` | **Actualizado en Fase 2** (ver Sección F de `19_PHASE2_CORE_HARDENING.md`) | **KEEP** | Ya no describe el proyecto como "esqueleto" |
| Gradle Wrapper | Ausente | **CREAR** | Mejora de reproducibilidad, no bloqueante; no tocado en esta fase |
| Cualquier componente FLAC/monolítico/validadores/`0x0205`/`0x0206` | No existen | **N/A — construir desde cero, en la fase de investigación siguiente** | Explícitamente fuera de alcance de esta fase (Sección 0/23 del prompt maestro de Fase 2) |

**Ningún componente existente se clasifica como REPLACE o REMOVE**, ni antes ni después de la Fase 2. Todas las correcciones fueron quirúrgicas sobre código ya reutilizable, nunca reescrituras.
