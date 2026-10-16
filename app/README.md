# :app

**Tipo:** Android application · **Carpeta:** `app/` · **Depende de:** `:core:dwp`, `:core:sampler-api`, `:platform:sampler-android`, `:platform:midi`

## Responsabilidad

La aplicación: interfaz Jetpack Compose, ViewModel (fachada de presentación), capa de sesión (proyecto, reproducción, mezclador, efectos, MIDI) y raíz de composición que enchufa las implementaciones concretas.

## API principal

`MainActivity`, `DwpCreatorApplication`, `DwpCreatorViewModel`, `AppContainer`

## Qué NO debe contener

Procesamiento de audio, formato DWP y decodificación MIDI: llega a ellos solo a través de los módulos de `core/` y `platform/`.

## Capas internas

| Paquete | Rol |
|---|---|
| `ui.screens`, `ui.components.*` | Pantallas y componentes Compose (un componente por archivo) |
| `ui.state`, `ui.theme` | Estado de UI y tema |
| `viewmodel` | `DwpCreatorViewModel`: fachada que compone la sesión y la expone a la UI |
| `session.project` | Estado del proyecto y casos de uso (cargar, renombrar, exportar) |
| `session.playback` | Tocar notas (`NotePlayer`) y mini-mezclador (`MixerController`) |
| `session.fx` | Voz y efectos (`FxController`) |
| `session.midi` | Conexión MIDI y enrutado de notas |
| `di` | Raíz de composición (`AppContainer`, `AppServices`) |

## Contenido

104 archivos de producción · 54 pruebas en 9 clases.

| Paquete (`com.jvk.dwpcreator.…`) | Archivos |
|---|---:|
| `com.jvk.dwpcreator` | 2 |
| `di` | 2 |
| `session` | 1 |
| `session.fx` | 1 |
| `session.midi` | 2 |
| `session.playback` | 2 |
| `session.project` | 13 |
| `ui.components.common` | 2 |
| `ui.components.dialogs` | 4 |
| `ui.components.effects` | 3 |
| `ui.components.mixer` | 11 |
| `ui.components.piano` | 3 |
| `ui.components.sample` | 3 |
| `ui.components.sampler` | 4 |
| `ui.components.sampler.voice` | 14 |
| `ui.components.sampler.widgets` | 13 |
| `ui.components.status` | 4 |
| `ui.components.topbar` | 2 |
| `ui.screens` | 3 |
| `ui.state` | 5 |
| `ui.theme` | 8 |
| `viewmodel` | 2 |

## Pruebas

```bash
gradle :app:testDebugUnitTest
```

Reglas del proyecto (se comprueban en CI con `python3 tools/check_architecture.py`): un tipo por archivo,
dependencias solo hacia abajo y sin ciclos. Ver `Doc/72_MODULAR_ARCHITECTURE_REFACTOR.md`.
