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
// scratch file and the unified -Proborazzi.verify flag reaches the harness as
// `tango.golden.verify` (a MISSING golden then fails instead of being recorded).
roborazziGoldens {
    harnessVerifyProperty = "tango.golden.verify"
}

dependencies {
    implementation(projects.games.tango)
    implementation(projects.core.game)
    implementation(projects.core.gameUi)
    implementation(projects.core.designsystem)

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.koin.core)
}
