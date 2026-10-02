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
include(":app")
// Módulo de Macrobenchmark que genera el Baseline Profile real -- ver
// Doc/47. Deliberadamente NO participa en `assembleRelease` de `:app`
// (`baselineProfile.automaticGenerationDuringBuild = false` en
// `app/build.gradle.kts`): solo corre bajo demanda, vía su propio workflow
// de GitHub Actions, para no acoplar el pipeline de release (Doc/45, ya
// funcionando) a esta infraestructura de emulador nueva.
include(":baselineprofile")
