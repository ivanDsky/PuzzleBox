plugins {
    alias(libs.plugins.puzzlebox.android.feature.impl)
    alias(libs.plugins.puzzlebox.android.roborazzi)
}

android {
    namespace = "com.ivandsky.puzzlebox.insights"
}

dependencies {
    // InsightsKey, InsightsRecapKey, InsightsStatsKey: the keys the entry builder registers routes for.
    implementation(projects.feature.insights.api)
    // ReplayKey: the key a Fastest solve row opens; the play feature's api, never its impl.
    implementation(projects.feature.play.api)
    // InsightsFlag implements ReleaseFlagKey, and the module declares its set with flagSet (ADR-0022).
    implementation(projects.core.common)
    // CloudSync.restoring: the recap and the hub wait out the first restore before they say there is nothing.
    implementation(projects.core.data)
    // ObserveGameHistory and isSolvedDaily: the one history the recap counts from; RankWindow for a period's friends read.
    implementation(projects.core.domain)
    // GameRegistry: the listed games' order and display names.
    implementation(projects.core.game)
    // RecordVerdict: the one personal-best rule, which the recap replays over the history.
    implementation(projects.core.gameUi)
    // GameId in the recap cards.
    implementation(projects.core.model)

    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.koin.android)

    testImplementation(projects.core.testing)
    testImplementation(libs.turbine)
}
