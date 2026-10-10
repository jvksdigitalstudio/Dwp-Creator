plugins {
    id("com.android.application") version "8.3.2" apply false
    // Kotlin 2.0.21 (K2) -- ver Doc/46. Sustituye a 1.9.23.
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    // Plugin oficial del compilador de Compose para Kotlin 2.0+: reemplaza
    // el mecanismo antiguo (`composeOptions { kotlinCompilerExtensionVersion }`
    // dentro de `app/build.gradle.kts`, ligado a una versión de artefacto
    // Maven aparte que había que llevar sincronizada a mano con la versión
    // de Kotlin). Con Kotlin 2.0+ el compilador de Compose vive DENTRO del
    // propio compilador de Kotlin (K2) -- este plugin solo lo activa, y su
    // versión debe ser exactamente la misma que la de Kotlin de arriba.
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
    // Módulo de test instrumentado (`:baselineprofile`, ver Doc/47) --
    // mismo mecanismo de plugin `com.android.application`/AGP, misma
    // versión, aplicado a un tipo de módulo distinto (`com.android.test`).
    id("com.android.test") version "8.3.2" apply false
    // Plugin de AndroidX que genera y empaqueta el Baseline Profile real a
    // partir del test de Macrobenchmark del módulo `:baselineprofile`.
    id("androidx.baselineprofile") version "1.3.3" apply false
}
