pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "DwpCreator"

// ---------------------------------------------------------------------------
// Estructura de carpetas = estructura de módulos (ver Doc/72_MODULAR_ARCHITECTURE_REFACTOR.md):
//
//   app/                      aplicación Android (UI Compose, ViewModel, sesión, DI)
//   core/                     lógica de negocio en Kotlin PURO (JVM, sin Android)
//   platform/                 integraciones con Android (audio, MIDI)
//   baselineprofile/          herramienta de rendimiento (Baseline Profile)
//
// Dirección de las dependencias (solo hacia abajo, sin ciclos):
//
//   :app ──► :platform:sampler-android ──► :core:sampler-engine ──► :core:sampler-api
//     │                 │                          └─────────────► :core:codec
//     ├──► :platform:midi                                               ▲
//     ├──► :core:dwp ───────────────────────────────────────────────────┘
//     └──► :core:sampler-api
//
// `tools/check_architecture.py` hace cumplir estas reglas en CI.
// ---------------------------------------------------------------------------

// Aplicación (UI Compose, ViewModel, capa de sesión y raíz de composición).
include(":app")

// core/ -- Kotlin puro: sus pruebas corren en la JVM, sin emulador.
include(":core:codec")           // decodificación WAV y códec FLAC
include(":core:dwp")             // formato DWP, exportador monolítico y E/S de proyectos zip
include(":core:sampler-api")     // modelo de parámetros, estados, presets y contratos del sampler
include(":core:sampler-engine")  // motor de audio: DSP, voces, mezcla y caché de muestras

// platform/ -- módulos de biblioteca Android.
include(":platform:sampler-android") // salida de audio (AudioTrack), reproductor y persistencia de ajustes
include(":platform:midi")            // entrada MIDI de Android y decodificación de mensajes

// Módulo de Macrobenchmark que genera el Baseline Profile real -- ver
// Doc/47. Deliberadamente NO participa en `assembleRelease` de `:app`
// (`baselineProfile.automaticGenerationDuringBuild = false` en
// `app/build.gradle.kts`): solo corre bajo demanda, vía su propio workflow
// de GitHub Actions, para no acoplar el pipeline de release (Doc/45, ya
// funcionando) a esta infraestructura de emulador nueva.
include(":baselineprofile")
