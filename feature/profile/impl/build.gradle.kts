plugins {
    alias(libs.plugins.puzzlebox.android.feature.impl)
}

android {
    namespace = "com.ivandsky.puzzlebox.profile"
}

dependencies {
    implementation(projects.feature.profile.api)
    // Identity + leaderboard contracts (IdentityService, LeaderboardEntry) in the VM/screen.
    implementation(projects.core.data)
    // ObserveTodayLeaderboard (today's board) and ObserveGameStats/ObserveGameHistory (the record).
    implementation(projects.core.domain)
    // Per-game brands (GamePlayRegistry) and GameGlyph for the game rows.
    implementation(projects.core.gameUi)
    // A game row opens History (api edge only).
    implementation(projects.feature.history.api)
    // GoogleIdTokenProvider: the sign-in seam the VM asks for a token.
    implementation(projects.core.auth)

    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.koin.android)

    testImplementation(projects.core.testing)
    testImplementation(libs.mockito.core)
}
