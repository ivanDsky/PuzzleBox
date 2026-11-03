plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.ivandsky.puzzlebox.play"
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
    implementation(project(":core:common"))
    implementation(project(":core:data"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:game"))
    implementation(project(":core:game-ui"))
    // F1 module map: FEAT → GAMES is a sanctioned edge — the play screens consume the
    // plugin surfaces (rules/score/renderer models, codecs), never engine internals.
    implementation(project(":games:zip"))
    // Queens play wiring (queens-10): session/codec/plugin from :games:queens, the
    // interactive board + controls from :games:queens-ui — the same plugin-surface edge
    // Zip's play screen uses (F1 module map FEAT → GAMES).
    implementation(project(":games:queens"))
    implementation(project(":games:queens-ui"))
    implementation(project(":games:tango"))
    implementation(project(":games:tango-ui"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material3.windowsizeclass)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.koin.androidx.compose)

    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.material3)
    testImplementation(libs.androidx.compose.material.icons.core)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.androidx.compose.ui.test.manifest)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    // T25 gate matrix: Roborazzi runtime API over Robolectric (the designsystem capture
    // path — the gradle plugin cannot apply on AGP 9), writing review-page PNGs.
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
}

// Screenshot tests default to recording (the T25 matrix test writes straight to the
// diagrams tree); -Proborazzi.verify mirrors the designsystem goldens contract.
val verifySnapshots = providers.gradleProperty("roborazzi.verify").isPresent

tasks.withType<Test>().configureEach {
    systemProperty("roborazzi.test.record", (!verifySnapshots).toString())
    systemProperty("roborazzi.test.verify", verifySnapshots.toString())
}
