plugins {
    alias(libs.plugins.puzzlebox.android.feature.impl)
}

android {
    namespace = "com.ivandsky.puzzlebox.play"
}

dependencies {
    implementation(projects.core.navigation)
    implementation(projects.feature.play.api)
    // Solving and leaving play unwind to Home's key (api edge only).
    implementation(projects.feature.home.api)
    implementation(projects.core.common)
    implementation(projects.core.data)
    // The picker's today's set (ObserveTodaySet, spec A14).
    implementation(projects.core.domain)
    implementation(projects.core.game)
    // ADR-0001/ADR-0007: play is game-agnostic — every game reaches it through the
    // core:game-ui play seam (GamePlayUi/GamePlayController), never a games:* edge.
    implementation(projects.core.gameUi)

    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(projects.core.testing)
    // PlayBackStackTest: a replay pushed from History still unwinds to Home.
    testImplementation(projects.feature.history.api)
}
