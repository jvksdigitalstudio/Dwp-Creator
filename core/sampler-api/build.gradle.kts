import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.kotlin.jvm")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

// Modelo de parámetros, estados, presets y contratos (SamplerPlayback, FxStateStore).
// Sin dependencias: es la base común de la UI y del motor.
dependencies {
    testImplementation("junit:junit:4.13.2")
}
