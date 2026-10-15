plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.jvk.dwpcreator.sampler.android"
    compileSdk = 34

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    // `api`: SamplePlayer implementa SamplerPlayback y devuelve AudioStats (ambos de :core:sampler-api).
    api(project(":core:sampler-api"))
    // El motor es un detalle de implementación: quien use este módulo no lo ve.
    implementation(project(":core:sampler-engine"))

    testImplementation("junit:junit:4.13.2")
}
