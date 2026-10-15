# 11 — AUDITORÍA DE DOCUMENTACIÓN EXISTENTE (vs. implementación real)

`[CODE]` Documentación localizada en el proyecto original (antes de esta auditoría): únicamente `README.md` (52 líneas) y comentarios KDoc dentro de cada archivo `.kt` (abundantes y, en general, de alta calidad y honestos sobre limitaciones — ver ejemplos abajo). No existía ninguna carpeta `docs/`, `Doc/` ni archivos `.md` adicionales antes de esta auditoría.

## README.md — desactualizado, confirmado por evidencia

> **✅ CORREGIDO EN FASE 2** (`Doc/19_PHASE2_CORE_HARDENING.md`): el README fue actualizado para reflejar el estado real del proyecto. Se conserva el hallazgo original completo abajo, tal como exige la Sección 22 del prompt maestro ("no borres información útil, indica por qué fue corregida").

`[CODE]` El README (versión original, antes de esta corrección) afirmaba: *"Este commit es el Paso 1 de 7: esqueleto del proyecto [...] Todavía no incluye el motor DWP, ni el motor de audio, ni MIDI"*.

`[CODE]` Esto es **falso respecto al código entregado**, confirmado por:
- Existe un motor DWP completo y probado (`DwpEngine`, `DwpTokenizer`, `DwpDocument` + 13 tests contra archivo real — 9 de Fase 0 + 4 añadidos en Fase 1; el "8" de la versión original de este documento era incorrecto incluso antes de Fase 1, ver `17_AUDIT_ERRATA.md` E-09).
- Existe un motor de audio completo (`WavDecoder`, `PcmConverter`, `SamplePlayer`).
- Existe soporte MIDI completo (`MidiInputManager`).
- El propio comentario de `MainActivity.kt` dice literalmente: *"Paso 7 complete: the full pipeline."*

`[INFERENCE]` El README quedó congelado en un commit temprano ("Paso 1") y nunca se actualizó a medida que el proyecto avanzó hasta el "Paso 7" — es un caso de libro de la Sección 2 del prompt de trabajo: *"la existencia de un README no demuestra el estado real"*, y aquí ocurre lo inverso también: el README subestima drásticamente lo que el código ya hace.

## Comentarios KDoc internos — mayormente precisos y con buena práctica de evidencia

`[CODE]` A diferencia del README, los comentarios KDoc dentro del código **sí están alineados con la implementación real** y, en varios casos, ya siguen una disciplina de evidencia similar a la exigida en este prompt de trabajo (ej. `DwpBlock.kt` dice explícitamente *"as verified by binary audit of a real FL Studio Desktop DirectWave export"*; `DwpEngine.replaceSampleAudio` documenta un *"KNOWN UNVERIFIED RISK"* de forma honesta). Esto se valora positivamente y se conserva.

`[CODE]` Único punto de desalineación leve encontrado en comentarios: `ZipProjectExporter.kt` describe un bug histórico ya corregido ("Before this fix, export() only ever wrote..."), lo cual es útil como registro de decisión pero mezcla narrativa de changelog con documentación de comportamiento actual — no es incorrecto, solo podría separarse en el futuro (no se toca en esta fase).

## Copy de UI — un caso de desalineación menor

`[CODE]` `LoadEmptyState.kt` muestra al usuario: *"Formatos soportados: .zip (dwp + wav), .dwp"*. `ZipProjectLoader.load` **solo sabe leer un `.zip`** (usa `ZipInputStream` incondicionalmente); no hay ninguna rama de código que detecte y cargue un `.dwp` suelto. Un usuario que intente cargar un `.dwp` sin comprimir recibiría, en el mejor caso, un error de `ZipLoadException` con un mensaje confuso ("no contiene ningún .dwp"), no el comportamiento que el texto promete. Clasificado como `RISK-03` en `13_RISK_REGISTER.md`.

## Regla de actualización aplicada (Sección 37 del prompt de trabajo)

No se elimina la información existente incorrecta; se documenta aquí el desfase y se recomienda (sin ejecutarlo en esta fase, por estar fuera del alcance de "solo auditar") que el README se actualice para reflejar el estado real ("Paso 7 completo") en la próxima fase de implementación, conservando su valor útil (instrucciones de Termux/GitHub Actions, que siguen siendo precisas y se verificaron contra `.github/workflows/build.yml` real).
