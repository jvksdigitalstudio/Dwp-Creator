plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    // Compilador de Compose K2 (Kotlin 2.0+) -- ver Doc/46. Sustituye al
    // mecanismo antiguo (bloque `composeOptions` más abajo, ya retirado).
    id("org.jetbrains.kotlin.plugin.compose")
    // Consume el Baseline Profile generado por el módulo `:baselineprofile`
    // (ver Doc/47) y lo empaqueta en `app/src/main/baseline-prof.txt`.
    id("androidx.baselineprofile")
}

android {
    namespace = "com.jvk.dwpcreator"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.jvk.dwpcreator"
        minSdk = 26
        targetSdk = 34
        versionCode = 12
        versionName = "0.7.0-release-r8-k2"
    }

    buildTypes {
        release {
            // Hasta ahora `false`: el pipeline real de este proyecto (ver
            // `.github/workflows/build.yml`, Doc/45) solo generaba
            // `assembleDebug` -- el APK que el usuario instala y prueba en
            // su tablet NUNCA pasaba por R8/minify/shrink de recursos, y
            // Compose en modo debug añade comprobaciones y hooks de tooling
            // extra que no existen en una build real. Eso pesa mucho más de
            // lo que cualquier optimización a nivel de composable puede
            // compensar -- es la causa más determinante del "lag pesado"
            // reportado al scrollear en un dispositivo real.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Firma con la config de "debug" (autogenerada por AGP, sin
            // ningún secreto que gestionar) -- deliberado y documentado: el
            // objetivo aquí es que el APK de release, ya optimizado por R8,
            // se pueda seguir instalando directo desde el artefacto que
            // sube GitHub Actions, igual que el debug de siempre. Esto NO
            // es una firma válida para publicar en Play Store -- ese es un
            // paso aparte, deliberado, con su propio keystore de producción
            // gestionado como secreto de repositorio, fuera del alcance de
            // este cambio.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

// Strong Skipping Mode -- ver Doc/45 para el diagnóstico completo (el
// segundo problema real detrás del lag, además de la build debug: `Map`/
// lambdas capturados por el `LazyColumn` de `MainScreen` desactivando el
// "skip" de recomposición de cada `SampleRow`). Con el compilador K2
// (Kotlin 2.0.21, ver arriba y Doc/46) ya no se activa con el flag
// experimental del compilador antiguo -- el propio plugin
// `org.jetbrains.kotlin.plugin.compose` expone esta opción como parte de su
// DSL oficial y estable. Se deja explícito (`true`) en vez de confiar en el
// valor por defecto de la versión del plugin: si una futura versión cambia
// el default, este proyecto sigue pidiéndolo a propósito.
composeCompiler {
    enableStrongSkippingMode.set(true)
}

// Configuración del consumo del Baseline Profile -- ver Doc/47.
baselineProfile {
    // NO generar (ni exigir) el perfil automáticamente como parte de
    // `assembleRelease` -- deliberado: desacopla el pipeline de release
    // normal (Doc/45, ya confirmado funcionando por el usuario en su
    // tablet) de la infraestructura de emulador de `:baselineprofile`.
    // Generar el perfil real sigue siendo posible explícitamente con
    // `./gradlew :app:generateReleaseBaselineProfile` (o vía el workflow
    // dedicado de GitHub Actions, ver `.github/workflows/baseline-profile.yml`);
    // el resultado se copia a mano a `app/src/main/baseline-prof.txt`
    // -- que ya se empaqueta en cada release normal vía `profileinstaller`
    // (Doc/46), tenga o no contenido real todavía.
    automaticGenerationDuringBuild = false
}
// Doc/59: `useConnectedDevices` NO es una propiedad válida aquí -- este es
// el bloque del módulo CONSUMIDOR (`:app`). Esa propiedad solo existe en el
// bloque `baselineProfile { }` del módulo PRODUCTOR (`:baselineprofile`,
// ver `baselineprofile/build.gradle.kts`), que es donde de verdad
// corresponde decidir qué dispositivo genera el perfil. Agregarla aquí en
// el Doc/58 rompió `:app:assembleRelease` -- `Unresolved reference:
// useConnectedDevices` -- es decir, tumbó el pipeline de release real que
// el usuario ya tenía funcionando. Corregido: se retira de aquí por
// completo; el `baselineProfile { }` del módulo `:baselineprofile` ya la
// tiene correctamente.

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.0")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    val composeBom = platform("androidx.compose:compose-bom:2024.05.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.animation:animation")

    debugImplementation("androidx.compose.ui:ui-tooling")

    // Instala en el dispositivo, en segundo plano y en los primeros
    // arranques, el perfil de referencia (`app/src/main/baseline-prof.txt`
    // -- ver Doc/46) que le dice a ART qué métodos compilar por adelantado
    // (AOT) en vez de esperar al JIT normal. Beneficio real incluso para un
    // APK instalado fuera de Play Store (que es como se distribuye este
    // proyecto, vía GitHub Actions): sin esta librería, un
    // `baseline-prof.txt` en el APK queda ahí sin usarse -- es esta
    // dependencia la que de verdad lo aplica en el dispositivo.
    implementation("androidx.profileinstaller:profileinstaller:1.3.1")

    // Fuente real del Baseline Profile -- ver Doc/47. Le dice al plugin
    // `androidx.baselineprofile` (aplicado arriba) dónde está el módulo de
    // Macrobenchmark que produce el perfil, para las tareas
    // `generate*BaselineProfile` bajo demanda (no automáticas, ver bloque
    // `baselineProfile { }` de arriba).
    baselineProfile(project(":baselineprofile"))

    testImplementation("junit:junit:4.13.2")
}
