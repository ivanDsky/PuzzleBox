// Compose play UI for both Sudoku games (mirrors games/queens-ui): controller, board, tutorial.
plugins {
    alias(libs.plugins.puzzlebox.android.library.compose)
    alias(libs.plugins.puzzlebox.android.roborazzi)
}

android {
    namespace = "com.ivandsky.puzzlebox.games.sudokuui"
}

dependencies {
    implementation(projects.games.sudoku)
    implementation(projects.core.game)
    implementation(projects.core.gameUi)
    implementation(projects.core.designsystem)
    implementation(projects.core.model)

    implementation(libs.kotlinx.coroutines.core)
    // Plugin-adjacent Koin wiring (ADR-0001): one ui module per game id, collected by `app`.
    implementation(libs.koin.core)
}
