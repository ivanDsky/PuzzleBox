// Pure-Kotlin Zip game module (A16): engine, variant data, versioned codec, renderer
// model, plugin. No Compose, no Android, no Room. The contracts' first real consumer —
// it must never require a :core:game edit (F1 §5).
plugins {
    id("org.jetbrains.kotlin.jvm") // version-less: AGP's built-in Kotlin owns the classpath
    alias(libs.plugins.kotlin.serialization)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}

dependencies {
    api(project(":core:game"))
    // Direct GameId use (ZipDefinition.id); the type already flows to consumers through
    // :core:game's api, so implementation is enough here.
    implementation(project(":core:model"))
    // Wire parser: trailGamePuzzle JSON -> typed puzzle (unknown fields ignored).
    implementation(libs.kotlinx.serialization.json)
    // Plugin registration (ADR-0001): one Koin module, collected by `app`.
    implementation(libs.koin.core)

    testImplementation(libs.junit)
}
