plugins {
    id("com.android.test")
    id("org.jetbrains.kotlin.android")
    id("androidx.baselineprofile")
}

android {
    namespace = "com.jvk.dwpcreator.baselineprofile"
    compileSdk = 34

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    defaultConfig {
        // API 28 -- mínimo real que exige `BaselineProfileRule` para poder
        // volcar el perfil de ART sin necesitar un dispositivo rooteado
        // (usa el mecanismo `pm dump-profile`, disponible desde API 28).
        minSdk = 28
        targetSdk = 34

        // Sin esto AGP usa el ejecutor antiguo `android.test.InstrumentationTestRunner`,
        // que solo entiende pruebas JUnit3 e IGNORA las `@Test` de JUnit4: el run #6
        // terminó en verde pero con `tests="0"` / `OK (0 tests)` y sin generar ningún
        // perfil (Doc/53). `AndroidJUnitRunner` es el que descubre y ejecuta
        // `BaselineProfileGenerator`.
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Este módulo no compila una app propia: instrumenta a `:app` de
    // verdad, sobre su APK de `release` (mismo build que se distribuye,
    // ver Doc/45) -- el perfil resultante tiene que reflejar el mismo
    // bytecode que corre en la tablet del usuario, no una build debug.
    targetProjectPath = ":app"

    testOptions {
        // Doc/58: este bloque de Gradle Managed Devices YA NO es el camino
        // activo -- se dejó tal cual, sin borrar, como referencia de lo que
        // se intentó (Docs 54-57: tres cuelgues sin causa diagnosticable con
        // la imagen ATD, y el mismo error real incluso con la imagen normal
        // y animaciones desactivadas). El workflow ahora usa
        // `reactivecircus/android-emulator-runner`, que arranca su propio
        // emulador y lo conecta por adb -- Gradle lo trata como "dispositivo
        // conectado" (`useConnectedDevices = true`, ver más abajo y
        // `app/build.gradle.kts`), no como uno gestionado por sí mismo.
        managedDevices {
            devices {
                // Doc/57: la imagen "aosp-atd" (runs #8, #9, #10) se
                // colgó las TRES veces dentro de la tarea de Gradle, con y sin
                // `require64Bit`, hasta agotar el tiempo límite -- nunca dio
                // un error concreto que se pudiera corregir. La imagen normal
                // "aosp" en API 34 (run #7), en cambio, SÍ completa rápido
                // (3 min) y falla con una excepción real y diagnosticable
                // (`Unable to confirm activity launch completion` en
                // `amStartAndWait`, ver Doc/54). Se vuelve a esa imagen -- un
                // fallo rápido con causa clara es mejor que un cuelgue sin
                // ninguna pista -- y el error real se ataca directo en el
                // propio test (ver `BaselineProfileGenerator.kt`,
                // desactivando animaciones antes de arrancar la Activity,
                // que es la mitigación documentada para ese error exacto).
                create<com.android.build.api.dsl.ManagedVirtualDevice>("pixel6Api34") {
                    device = "Pixel 6"
                    apiLevel = 34
                    systemImageSource = "aosp"
                }
            }
        }
    }
}

// Configuración del propio plugin de Baseline Profile -- Doc/58: se
// consume desde un dispositivo ya conectado (el emulador que arranca
// `reactivecircus/android-emulator-runner` en el workflow), no desde el
// dispositivo gestionado de arriba (que se deja como referencia, sin uso).
baselineProfile {
    useConnectedDevices = true
}

dependencies {
    // Sin `espresso-core` (Doc/52): el test solo usa UiAutomator y Macrobenchmark;
    // Espresso no se importa en ningún sitio y arrastraba `hamcrest-library`, una de
    // las descargas que Maven Central rechazó con 429 en el primer run (Doc/48).
    implementation("androidx.test.ext:junit:1.2.1")
    implementation("androidx.test:runner:1.6.1")
    implementation("androidx.test.uiautomator:uiautomator:2.3.0")
    implementation("androidx.benchmark:benchmark-macro-junit4:1.3.3")
}
