# :platform:sampler-android

**Tipo:** Android library · **Carpeta:** `platform/sampler-android/` · **Depende de:** `:core:sampler-api` (con `api`), `:core:sampler-engine` (privada)

## Responsabilidad

Llevar el motor al dispositivo: stream de salida (`AudioTrack`), hilo de audio, `SamplePlayer` (implementa `SamplerPlayback`) y `SamplerFxPreferences` (implementa `FxStateStore`).

## API principal

`SamplePlayer`, `SamplerFxPreferences`

## Qué NO debe contener

Algoritmos de DSP o de síntesis (viven en el motor) y toda lógica de UI.

## Contenido

3 archivos de producción · 0 pruebas en 0 clases.

| Paquete (`com.jvk.dwpcreator.…`) | Archivos |
|---|---:|
| `sampler.android` | 3 |

## Pruebas

```bash
gradle :platform:sampler-android:testDebugUnitTest
```

Reglas del proyecto (se comprueban en CI con `python3 tools/check_architecture.py`): un tipo por archivo,
dependencias solo hacia abajo y sin ciclos. Ver `Doc/72_MODULAR_ARCHITECTURE_REFACTOR.md`.
