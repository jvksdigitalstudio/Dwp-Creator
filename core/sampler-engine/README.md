# :core:sampler-engine

**Tipo:** Kotlin JVM (sin Android) · **Carpeta:** `core/sampler-engine/` · **Depende de:** `:core:sampler-api` y `:core:codec` (ambos con `api`)

## Responsabilidad

El *cómo suena*: efectos (drive, delay, reverb, chorus, limitador), voces (muestra, filtro SVF, ADSR, LFO, glide), mezcla polifónica y caché de muestras decodificadas.

## API principal

`SamplerEngine`, `DecodedSampleCache`, `CachedSample`, `MasterFxChain` y las clases de `engine.dsp`/`engine.voice`

## Qué NO debe contener

Android, UI, persistencia, ni decisiones de qué tecla dispara qué muestra.

## Contenido

28 archivos de producción · 160 pruebas en 16 clases.

| Paquete (`com.jvk.dwpcreator.…`) | Archivos |
|---|---:|
| `sampler.engine` | 1 |
| `sampler.engine.dsp` | 11 |
| `sampler.engine.sample` | 2 |
| `sampler.engine.voice` | 14 |

## Pruebas

```bash
gradle :core:sampler-engine:test
```

Reglas del proyecto (se comprueban en CI con `python3 tools/check_architecture.py`): un tipo por archivo,
dependencias solo hacia abajo y sin ciclos. Ver `Doc/72_MODULAR_ARCHITECTURE_REFACTOR.md`.
