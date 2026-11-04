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
    // ObserveTodayLeaderboard: today's board is the featured game's.
    implementation(projects.core.domain)

    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.koin.android)
    // Ticket 16: Credential Manager Google sign-in feeds the backend an ID token.
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services)
    implementation(libs.googleid)

    testImplementation(projects.core.testing)
    testImplementation(libs.mockito.core)
}
