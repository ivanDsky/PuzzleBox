plugins {
    alias(libs.plugins.puzzlebox.android.library)
    alias(libs.plugins.puzzlebox.android.room)
}

android {
    namespace = "com.ivandsky.puzzlebox.database"

    sourceSets {
        // WHY: MigrationTestHelper (ticket-04 deferral harness) reads the committed schema
        // JSONs (exported by puzzlebox.android.room) from the merged test ASSETS.
        getByName("test") { assets.directories.add("$projectDir/schemas") }
    }
}

dependencies {
    // PuzzleBoxDatabase is a RoomDatabase in this module's public API (core:data runs
    // withTransaction over it and builds in-memory instances in its tests).
    api(libs.androidx.room.runtime)
    implementation(libs.koin.core)

    // Migration harness (ticket-04 deferral): MigrationTestHelper replays committed schemas.
    testImplementation(libs.androidx.room.testing)
}
