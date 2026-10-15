# :core:sampler-api

**Tipo:** Kotlin JVM (sin Android) · **Carpeta:** `core/sampler-api/` · **Depende de:** —

## Responsabilidad

El *qué* del sampler: parámetros y sus escalas, estado de voz y de efectos, presets, formateo de valores, curvas de previsualización y los contratos que desacoplan la aplicación del motor (`SamplerPlayback`, `FxStateStore`). Es la base común de la UI y del motor.

## API principal

`SamplerParam`, `SamplerParams`, `SamplerFxState`, `VoiceState`, `FxPresets`, `ParamFormatter`, `FilterResponse`, `LfoWaveforms`, `SamplerPlayback`, `FxStateStore`, `AudioStats`

## Qué NO debe contener

Procesamiento de audio (DSP) y síntesis: eso es `:core:sampler-engine`. Tampoco nada de Android.

## Contenido

47 archivos de producción · 55 pruebas en 6 clases.

| Paquete (`com.jvk.dwpcreator.…`) | Archivos |
|---|---:|
| `sampler.api` | 3 |
| `sampler.api.curve` | 2 |
| `sampler.api.math` | 2 |
| `sampler.api.options` | 2 |
| `sampler.api.params` | 18 |
| `sampler.api.preset` | 4 |
| `sampler.api.rhythm` | 3 |
| `sampler.api.state` | 13 |

## Pruebas

```bash
gradle :core:sampler-api:test
```

Reglas del proyecto (se comprueban en CI con `python3 tools/check_architecture.py`): un tipo por archivo,
dependencias solo hacia abajo y sin ciclos. Ver `Doc/72_MODULAR_ARCHITECTURE_REFACTOR.md`.
