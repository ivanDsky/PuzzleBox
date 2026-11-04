plugins {
    alias(libs.plugins.puzzlebox.android.feature)
}

android {
    namespace = "com.ivandsky.puzzlebox.history"
}

dependencies {
    implementation(projects.core.data)
    // HistoryViewModel resolves per-game names against the app's GameRegistry.
    implementation(projects.core.game)
    // ADR-0001: history is game-agnostic — replay sessions come from each game's plugin
    // through GetReplaySession, never a games:* edge.
    implementation(projects.core.domain)

    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.paging.compose)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.androidx.paging.testing)
    testImplementation(projects.core.testing)
}
