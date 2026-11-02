plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.ivandsky.puzzlebox.data"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    sourceSets {
        // WHY: MigrationTestHelper (ticket-04 deferral harness) reads the committed schema
        // JSONs from the merged test ASSETS (requires includeAndroidResources).
        getByName("test") { assets.srcDir("$projectDir/schemas") }
    }

    testOptions {
        unitTests { isIncludeAndroidResources = true }
    }
}

ksp {
    // WHY: exported schemas under schemas/ are the migration contract — committed on
    // every version bump of PuzzleBoxDatabase and referenced by migrations.
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    api(project(":core:common"))
    // Wire -> engine seam (ADR-0002): the LinkedIn adapter parses to ZipWireParser's input
    // and stores ZipCodec bytes; games/zip exposes :core:game as api.
    implementation(project(":games:zip"))
    // Queens feed (queens-06): LinkedInQueensDailySource + QueensUniquenessGate + codec bytes.
    implementation(project(":games:queens"))

    // DailyPuzzle/ThemeMode appear in the repository interfaces' public signatures.
    api(project(":core:model"))

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp.logging.interceptor)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.koin.core)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.robolectric)
    // Migration harness (ticket-04 deferral): MigrationTestHelper replays committed schemas.
    testImplementation(libs.androidx.room.testing)
}
