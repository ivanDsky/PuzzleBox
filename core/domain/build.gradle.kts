// Use cases (P7): the cross-feature rules History, Home and Profile share — merged game
// history, the featured game, the streak, the replay hand-off and today's leaderboard.
// Pure Kotlin sources (no Android, Compose or paging types); the module is an Android
// library only because :core:data is one and a JVM module cannot consume it (the same
// shape nowinandroid's core:domain takes).
plugins {
    alias(libs.plugins.puzzlebox.android.library)
}

android {
    namespace = "com.ivandsky.puzzlebox.domain"
}

dependencies {
    // Repository interfaces, GameRegistry and GameId appear in the use cases' signatures.
    api(projects.core.data)
    api(projects.core.game)
    api(projects.core.model)

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.koin.core)

    testImplementation(projects.core.testing)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
}
