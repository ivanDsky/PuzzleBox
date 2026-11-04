// Pure-Kotlin Queens game module (mirrors games/zip, A16/A3): engine, variant data,
// versioned codec, wire parser, renderer model, plugin. No Compose, no Android, no Room.
plugins {
    alias(libs.plugins.puzzlebox.jvm.library)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    api(projects.core.game)
    implementation(projects.core.model)
    implementation(libs.kotlinx.serialization.json)
    // Plugin registration (ADR-0001): one Koin module, collected by `app`.
    implementation(libs.koin.core)
}
