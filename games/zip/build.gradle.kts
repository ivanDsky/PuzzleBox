// Pure-Kotlin Zip game module (A16): engine, variant data, versioned codec, renderer
// model, plugin. No Compose, no Android, no Room. The contracts' first real consumer —
// it must never require a :core:game edit (F1 §5).
plugins {
    alias(libs.plugins.puzzlebox.jvm.library)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    api(projects.core.game)
    // Direct GameId use (ZipDefinition.id); the type already flows to consumers through
    // :core:game's api, so implementation is enough here.
    implementation(projects.core.model)
    // Wire parser: trailGamePuzzle JSON -> typed puzzle (unknown fields ignored).
    implementation(libs.kotlinx.serialization.json)
    // Plugin registration (ADR-0001): one Koin module, collected by `app`.
    implementation(libs.koin.core)
}
