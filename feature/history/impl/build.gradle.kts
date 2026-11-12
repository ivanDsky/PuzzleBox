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
    // HistoryViewModel resolves per-game names against the app's GameRegistry.
    implementation(projects.core.game)
    // ADR-0001: history is game-agnostic — play-again sessions come from each game's plugin
    // through LoadPastDay, never a games:* edge.
    implementation(projects.core.domain)
    // Game brands, glyphs and the calendar's day dots (identity §3).
    implementation(projects.core.gameUi)

    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.paging.compose)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.androidx.paging.testing)
    testImplementation(projects.core.testing)
}
