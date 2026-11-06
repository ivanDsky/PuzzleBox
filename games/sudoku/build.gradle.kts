// Pure-Kotlin Sudoku game module (mirrors games/queens): geometry, cells, commands, session,
// codecs, LinkedIn Mini Sudoku feed, plugin. No Compose, no Android, no Room.
plugins {
    alias(libs.plugins.puzzlebox.jvm.library)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    api(projects.core.game)
    implementation(projects.core.model)
    implementation(libs.kotlinx.serialization.json)
    // Plugin registration (ADR-0001): one Koin module per GameId, collected by `app`.
    implementation(libs.koin.core)
}
