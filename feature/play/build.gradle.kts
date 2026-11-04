plugins {
    alias(libs.plugins.puzzlebox.android.feature)
    // T25 gate matrix: VariantMatrixScreenshotTest writes review-page PNGs.
    alias(libs.plugins.puzzlebox.android.roborazzi)
}

android {
    namespace = "com.ivandsky.puzzlebox.play"
}

dependencies {
    implementation(projects.core.common)
    implementation(projects.core.data)
    implementation(projects.core.game)
    implementation(projects.core.gameUi)
    // F1 module map: FEAT → GAMES is a sanctioned edge — the play screens consume the
    // plugin surfaces (rules/score/renderer models, codecs), never engine internals.
    implementation(projects.games.zip)
    // Queens play wiring (queens-10): session/codec/plugin from :games:queens, the
    // interactive board + controls from :games:queens-ui — the same plugin-surface edge
    // Zip's play screen uses (F1 module map FEAT → GAMES).
    implementation(projects.games.queens)
    implementation(projects.games.queensUi)
    implementation(projects.games.tango)
    implementation(projects.games.tangoUi)

    implementation(libs.kotlinx.coroutines.core)
}
