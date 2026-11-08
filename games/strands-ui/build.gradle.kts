// Compose mapping for the Strands game (queens-ui precedent): StrandsRendererModel -> shared
// game-ui board. Pure render plus the play controller; every word rule stays in games:strands.
plugins {
    alias(libs.plugins.puzzlebox.android.library.compose)
    alias(libs.plugins.puzzlebox.android.roborazzi)
}

android {
    namespace = "com.ivandsky.puzzlebox.games.strandsui"

    testOptions {
        unitTests.all { test ->
            test.maxHeapSize = "2g"
        }
    }
}

dependencies {
    implementation(projects.games.strands)
    implementation(projects.core.common)
    implementation(projects.core.game)
    implementation(projects.core.gameUi)
    implementation(projects.core.designsystem)

    implementation(libs.kotlinx.coroutines.core)
    // Plugin-adjacent Koin wiring (ADR-0001): one ui module, collected by `app`.
    implementation(libs.koin.core)
}
