plugins {
    alias(libs.plugins.puzzlebox.android.library)
}

android {
    namespace = "com.ivandsky.puzzlebox.data"
}

dependencies {
    api(projects.core.common)
    // Game contracts (DailySource, GameRegistry, Puzzle, FeedTransport) appear in the
    // repositories' public signatures; no game module is ever a dependency (ADR-0001).
    api(projects.core.game)

    // ThemeMode/GameId appear in the repositories' public signatures.
    api(projects.core.model)

    // Sources behind the repositories; none of their types is in this module's public API.
    implementation(projects.core.database)
    implementation(projects.core.datastore)
    implementation(projects.core.network)

    implementation(libs.koin.core)

    testImplementation(projects.core.testing)
    testImplementation(libs.turbine)
    // DI-graph guard (ticket 03): koin-android provides androidContext(); koin-test the KoinTest harness.
    testImplementation(libs.koin.android)
    testImplementation(libs.koin.test)
}
