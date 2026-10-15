# 01 — ESTRUCTURA REAL DEL PROYECTO

> **Nota de vigencia:** este documento describe la estructura del **zip original entregado** (Fase 0, antes de crear `Doc/` y antes de los cambios de Fase 1). Sigue siendo correcto como registro histórico de ese punto de partida. Para el inventario fresco y vigente (33 `.kt`, 62 archivos totales, 32 tests), ver `03_CODE_INVENTORY.md` y `18_CANONICAL_STATE.md`.

`[CODE]` Árbol completo tal como existe en el zip entregado (`DwpCreator.zip`), obtenido con `find . -type f`, sin omitir nada:

```
DwpCreator/
├── .github/workflows/build.yml
├── .gitignore
├── README.md
├── build.gradle.kts                 (root)
├── settings.gradle.kts
├── gradle.properties
└── app/
    ├── build.gradle.kts
    └── src/
        ├── main/
        │   ├── AndroidManifest.xml
        │   ├── java/com/jvk/dwpcreator/
        │   │   ├── DwpCreatorApplication.kt
        │   │   ├── MainActivity.kt
        │   │   ├── audio/SamplePlayer.kt
        │   │   ├── midi/MidiInputManager.kt
        │   │   ├── domain/
        │   │   │   ├── audio/WavDecoder.kt
        │   │   │   ├── audio/PcmConverter.kt
        │   │   │   ├── dwp/DwpBlock.kt
        │   │   │   ├── dwp/DwpDocument.kt
        │   │   │   ├── dwp/DwpEngine.kt
        │   │   │   ├── dwp/DwpTokenizer.kt
        │   │   │   ├── dwp/SampleInfo.kt
        │   │   │   └── io/LoadedProject.kt, ZipProjectLoader.kt, ZipProjectExporter.kt
        │   │   ├── viewmodel/DwpCreatorViewModel.kt
        │   │   └── ui/
        │   │       ├── components/ (DwpTopBar, ImportingOverlay, LoadEmptyState,
        │   │       │                MidiDevicesDialog, PianoKeyBadge, RenameAllDialog,
        │   │       │                SampleRow, StatusBar)
        │   │       ├── screens/MainScreen.kt
        │   │       ├── state/DwpUiState.kt, PreviewData.kt
        │   │       └── theme/Color.kt, Theme.kt, Type.kt
        │   └── res/
        │       ├── values/strings.xml, themes.xml
        │       └── drawable/ic_launcher.xml
        └── test/
            ├── java/com/jvk/dwpcreator/domain/
            │   ├── audio/PcmConverterTest.kt, WavDecoderTest.kt
            │   ├── dwp/DwpEngineTest.kt
            │   └── io/ZipProjectIoTest.kt
            └── resources/Instrument.dwp   ← fixture binario REAL (47.307 bytes)
```

**Total: 45 archivos.** No hay `androidTest/` (sin tests instrumentados de Android), no hay `assets/` de la app, no hay JNI/NDK/C/C++, no hay shaders, no hay scripts auxiliares fuera del workflow de CI, no hay `gradlew`/wrapper.

## Observaciones estructurales

`[CODE]` La separación de paquetes es limpia y ya sigue, de facto, una arquitectura en capas:
- `domain/dwp` — núcleo binario DWP, puro Kotlin/JVM, sin dependencia de Android.
- `domain/audio` — decodificación WAV y conversión PCM, puro Kotlin/JVM.
- `domain/io` — orquestación de carga/exportación de proyecto (zip).
- `audio/`, `midi/` — capas que sí dependen de Android (`AudioTrack`, `android.media.midi`).
- `viewmodel/`, `ui/` — capa de presentación (Compose).

`[INFERENCE]` Esta separación (dominio puro-Kotlin vs. capas Android) es exactamente la que el proyecto necesitará conservar y extender cuando se añadan `FlacEncoder`, validadores, etc. — no hace falta reestructurar paquetes, solo añadir bajo `domain/dwp` y crear `domain/audio/flac` en el futuro.

`[CODE]` `res/` es mínimo: solo `strings.xml`, `themes.xml` y un ícono vectorial. No hay recursos de layout XML (toda la UI es Compose).
