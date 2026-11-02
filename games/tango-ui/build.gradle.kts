// Compose mapping for the Tango game (ZipBoardRenderer structure): TangoRendererModel ->
// shared game-ui board. Pure render; final visual design is ticket-04 scope.
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.ivandsky.puzzlebox.games.tangoui"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

dependencies {
    implementation(project(":games:tango"))
    implementation(project(":core:game"))
    implementation(project(":core:game-ui"))
    implementation(project(":core:designsystem"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.koin.core)

    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.material3)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.androidx.compose.ui.test.manifest)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
}

// Screenshot captures write to the review tree; -Proborazzi.verify mirrors the
// designsystem goldens contract (feature/play precedent).
val verifySnapshots = providers.gradleProperty("roborazzi.verify").isPresent

tasks.withType<Test>().configureEach {
    systemProperty("roborazzi.test.record", (!verifySnapshots).toString())
    systemProperty("roborazzi.test.verify", verifySnapshots.toString())
}
