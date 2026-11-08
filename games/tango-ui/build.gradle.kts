// Compose mapping for the Tango game (ZipBoardRenderer structure): TangoRendererModel ->
// shared game-ui board. Pure render.
plugins {
    alias(libs.plugins.puzzlebox.android.library.compose)
    alias(libs.plugins.puzzlebox.android.roborazzi)
}

android {
    namespace = "com.ivandsky.puzzlebox.games.tangoui"
}

// TangoBoardGoldenTest owns the compare (review I6): Roborazzi always records to its
// scratch file and the unified mode (record, verify or compare) reaches the harness as
// `tango.golden.mode`.
roborazziGoldens {
    harnessModeProperty = "tango.golden.mode"
}

dependencies {
    implementation(projects.games.tango)
    implementation(projects.core.game)
    implementation(projects.core.gameUi)
    implementation(projects.core.common)
    implementation(projects.core.designsystem)

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.koin.core)
}
