# :core:codec

**Tipo:** Kotlin JVM (sin Android) · **Carpeta:** `core/codec/` · **Depende de:** —

## Responsabilidad

Decodificar WAV (PCM 8/16/24/32 bits, float32 y `WAVE_FORMAT_EXTENSIBLE`), convertir PCM, leer regiones de bucle (`smpl`) y codificar/decodificar FLAC.

## API principal

`WavDecoder`, `DecodedWav`, `SampleFormat`, `PcmConverter`, `WavLoopPoints`, `SampleLoop`, `FlacEncoder`, `FlacDecoder`

## Qué NO debe contener

Nada del formato DWP, nada de reproducción en tiempo real, nada de Android.

## Contenido

21 archivos de producción · 104 pruebas en 8 clases.

| Paquete (`com.jvk.dwpcreator.…`) | Archivos |
|---|---:|
| `codec.flac` | 14 |
| `codec.wav` | 7 |

## Pruebas

```bash
gradle :core:codec:test
```

Reglas del proyecto (se comprueban en CI con `python3 tools/check_architecture.py`): un tipo por archivo,
dependencias solo hacia abajo y sin ciclos. Ver `Doc/72_MODULAR_ARCHITECTURE_REFACTOR.md`.
