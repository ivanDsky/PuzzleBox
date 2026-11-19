// Result, stats, daily-board and replay chrome that features share and no game UI imports.
// The play kit the games compile against stays in core:game-ui.
plugins {
    alias(libs.plugins.puzzlebox.android.library.compose)
    alias(libs.plugins.puzzlebox.android.roborazzi)
}

android {
    namespace = "com.ivandsky.puzzlebox.resultui"
}

dependencies {
    // Public signatures take play-kit types (RecordVerdict, GameReplayBoard, ReplayPlaybackState).
    api(projects.core.gameUi)
    implementation(projects.core.game)
    implementation(projects.core.designsystem)
    // CompletionRecordEmitter's write returns an AppResult.
    implementation(projects.core.common)
    // The daily board's leaderboard rows and today status; core:data arrives through domain's api.
    implementation(projects.core.domain)

    // PlayerAvatar draws a remote photo with Coil's AsyncImage.
    implementation(libs.coil.compose)
    implementation(libs.kotlinx.coroutines.core)

    // The replay tests build their timelines from core:testing's ReplayFixtures.
    testImplementation(projects.core.testing)
}
