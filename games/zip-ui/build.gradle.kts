// Zip's play surface (A16/F2 §7, ADR-0007): the ZipPlayUi registration, its controller
// (ZipSession -> renderer model, drag/undo/hint/clock) and the board composables.
// No fetch, record or navigation code — the play feature owns those game-agnostically.
plugins {
    alias(libs.plugins.puzzlebox.android.library.compose)
    // T25 gate matrix: VariantMatrixScreenshotTest writes review-page PNGs.
    alias(libs.plugins.puzzlebox.android.roborazzi)
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

    testImplementation(projects.core.testing)
}
