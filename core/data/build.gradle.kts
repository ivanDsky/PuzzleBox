plugins {
    alias(libs.plugins.puzzlebox.android.library)
    alias(libs.plugins.puzzlebox.android.room)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.ivandsky.puzzlebox.data"

    sourceSets {
        // WHY: MigrationTestHelper (ticket-04 deferral harness) reads the committed schema
        // JSONs (exported by puzzlebox.android.room) from the merged test ASSETS.
        getByName("test") { assets.srcDir("$projectDir/schemas") }
    }
}

dependencies {
    api(projects.core.common)
    // Game contracts (DailySource, GameRegistry, Puzzle, FeedTransport) appear in the
    // repositories' public signatures; no game module is ever a dependency (ADR-0001).
    api(projects.core.game)

    // ThemeMode/GameId appear in the repositories' public signatures.
    api(projects.core.model)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp.logging.interceptor)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.koin.core)

    testImplementation(libs.turbine)
    testImplementation(libs.okhttp.mockwebserver)
    // DI-graph guard (ticket 03): koin-android provides androidContext(); koin-test the KoinTest harness.
    testImplementation(libs.koin.android)
    testImplementation(libs.koin.test)
    // Migration harness (ticket-04 deferral): MigrationTestHelper replays committed schemas.
    testImplementation(libs.androidx.room.testing)
}
