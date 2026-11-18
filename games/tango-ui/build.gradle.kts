// Compose mapping for the Tango game (ZipBoardRenderer structure): TangoRendererModel ->
// shared game-ui board. Pure render.
plugins {
    alias(libs.plugins.puzzlebox.android.library.compose)
    alias(libs.plugins.puzzlebox.android.roborazzi)
}

android {
    namespace = "com.ivandsky.puzzlebox.games.tangoui"
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
