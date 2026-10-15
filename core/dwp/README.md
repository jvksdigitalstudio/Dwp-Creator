# :core:dwp

**Tipo:** Kotlin JVM (sin Android) · **Carpeta:** `core/dwp/` · **Depende de:** `:core:codec` (expuesto con `api`)

## Responsabilidad

El formato DWP de DirectWave: tokenizar y reserializar sin pérdida, renombrar (`DwpRenamer`), parchear el formato de audio (`DwpAudioPatcher`), listar muestras (`DwpSampleCatalog`), decidir qué muestra suena para una nota (`SampleZoneResolver`), generar el DWP monolítico con FLAC y leer/escribir los proyectos `.zip`.

## API principal

`DwpDocument`, `DwpRenamer`, `DwpAudioPatcher`, `DwpSampleCatalog`, `SampleZoneResolver`, `SampleInfo`, `MonolithicDwpProjectBuilder`, `ZipProjectLoader`, `ZipProjectExporter`, `LoadedProject`

## Qué NO debe contener

Reproducción de audio, UI, acceso a archivos de Android (recibe `InputStream`/`OutputStream`).

## Contenido

27 archivos de producción · 111 pruebas en 14 clases.

| Paquete (`com.jvk.dwpcreator.…`) | Archivos |
|---|---:|
| `dwp` | 11 |
| `dwp.io` | 5 |
| `dwp.monolithic` | 11 |

## Pruebas

```bash
gradle :core:dwp:test
```

Reglas del proyecto (se comprueban en CI con `python3 tools/check_architecture.py`): un tipo por archivo,
dependencias solo hacia abajo y sin ciclos. Ver `Doc/72_MODULAR_ARCHITECTURE_REFACTOR.md`.
