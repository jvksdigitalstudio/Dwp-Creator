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

// Pruebas contra el Instrument.dwp real de FL Studio (compartido con :app, ver /testdata).
sourceSets["test"].resources.srcDir(rootProject.file("testdata"))

dependencies {
    // `api`: DecodedWav, SampleLoop... aparecen en la firma pública de DwpAudioPatcher y del exportador.
    api(project(":core:codec"))

    testImplementation("junit:junit:4.13.2")
}
