// Compose mapping for the Queens game (zip-ui precedent): QueensRendererModel -> shared
// game-ui board. Pure render — no game logic; the state -> model call stays with the
// plugin/play feature.
plugins {
    alias(libs.plugins.puzzlebox.android.library.compose)
    alias(libs.plugins.puzzlebox.android.roborazzi)
}

android {
    namespace = "com.ivandsky.puzzlebox.games.queensui"

    testOptions {
        unitTests.all { test ->
            test.maxHeapSize = "2g"
        }
    }
}

dependencies {
    implementation(projects.games.queens)
    implementation(projects.core.common)
    implementation(projects.core.game)
    implementation(projects.core.gameUi)
    implementation(projects.core.designsystem)

    implementation(libs.kotlinx.coroutines.core)
    // Plugin-adjacent Koin wiring (ADR-0001): one ui module, collected by `app`.
    implementation(libs.koin.core)
}
