// Compose mapping for the Zip game (A16/F2 §7): ZipRendererModel -> shared game-ui board.
// Pure render — no game logic; the state -> model call stays in the play feature.
plugins {
    alias(libs.plugins.puzzlebox.android.library.compose)
}

android {
    namespace = "com.ivandsky.puzzlebox.games.zipui"
}

dependencies {
    implementation(projects.games.zip)
    implementation(projects.core.game)
    implementation(projects.core.gameUi)
    implementation(projects.core.designsystem)

    implementation(libs.kotlinx.coroutines.core)
    // Plugin-adjacent Koin wiring (ADR-0001): one ui module, collected by `app`.
    implementation(libs.koin.core)
}
