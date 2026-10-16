# core/ — lógica de negocio en Kotlin puro

Módulos **sin una sola línea de Android**: se compilan y se prueban en la JVM, sin emulador. Si un módulo
nuevo no necesita Android, va aquí.

| Módulo | Responsabilidad |
|---|---|
| [`codec`](codec/README.md) | WAV y FLAC |
| [`dwp`](dwp/README.md) | Formato DWP, DWP monolítico y proyectos zip |
| [`sampler-api`](sampler-api/README.md) | Parámetros, estados, presets y contratos del sampler |
| [`sampler-engine`](sampler-engine/README.md) | Motor de audio: DSP, voces, mezcla |

```
:app ──► :core:dwp ──► :core:codec
 └─────► :core:sampler-api ◄── :core:sampler-engine ──► :core:codec
```
