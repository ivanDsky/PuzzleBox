plugins {
    alias(libs.plugins.puzzlebox.android.feature.impl)
    // 83's History replay section goldens.
    alias(libs.plugins.puzzlebox.android.roborazzi)
}

android {
    namespace = "com.ivandsky.puzzlebox.history"
}

dependencies {
    implementation(projects.core.navigation)
    implementation(projects.feature.history.api)
    // A play-again day opens play's play-again key (api edge only).
    implementation(projects.feature.play.api)
    implementation(projects.core.data)
    // The hub's freshness line is behind a release flag (ADR-0022).
    implementation(projects.core.common)
    // The hub's board asks a signed-out player to sign in, in place.
    implementation(projects.core.auth)
    // The hub resolves per-game names against the app's GameRegistry.
    implementation(projects.core.game)
    // ADR-0001: history is game-agnostic — play-again sessions come from each game's plugin
    // through LoadPastDay, never a games:* edge.
    implementation(projects.core.domain)
    // Game brands, glyphs and the calendar's day dots (identity §3).
    implementation(projects.core.gameUi)
    // DailyBoard and its rows on the ranks page.
    implementation(projects.core.resultUi)

    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(projects.core.testing)
}
