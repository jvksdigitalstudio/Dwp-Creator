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

dependencies {
    // `api`: SamplerEngine consume y expone SamplerFxState, y CachedSample expone SampleLoop.
    api(project(":core:sampler-api"))
    api(project(":core:codec"))

    testImplementation("junit:junit:4.13.2")
}
