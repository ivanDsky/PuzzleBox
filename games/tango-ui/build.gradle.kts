// Compose mapping for the Tango game (ZipBoardRenderer structure): TangoRendererModel ->
// shared game-ui board. Pure render.
plugins {
    alias(libs.plugins.puzzlebox.android.library.compose)
    alias(libs.plugins.puzzlebox.android.roborazzi)
    alias(libs.plugins.ksp)
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

// Ticket 04: component mode, same as designsystem — only @ShowkaseComposable
// composables are indexed into the debug browser.
ksp {
    arg("requireShowkaseComposableAnnotation", "true")
}

dependencies {
    implementation(projects.games.tango)
    implementation(projects.core.game)
    implementation(projects.core.gameUi)
    implementation(projects.core.designsystem)

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.koin.core)

    // Debug A/B switcher (design-direction §8, ticket 04): Showkase-family surface —
    // same debug-only posture as designsystem (annotations in main, processor on kspDebug).
    implementation(libs.showkase.annotation)
    debugImplementation(libs.showkase)
    kspDebug(libs.showkase.processor)
}
