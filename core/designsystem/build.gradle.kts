plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.ivandsky.puzzlebox.designsystem"
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
            all { test ->
                test.maxHeapSize = "2g"
            }
        }
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)

    // Studio canvas tooling. @Preview/@PreviewLightDark live in main source next to
    // their components, so the annotation artifact must compile in every variant
    // (debugImplementation here would break release compilation of Buttons.kt); the
    // heavyweight Layoutlib renderer is debug-only. The app module already carries
    // its own debugImplementation(ui-tooling).
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // Showkase trial (spec A9/A10, keep-or-drop decided after the run). Debug-only
    // posture per design-previews-00: browser runtime debugImplementation, annotations
    // in main source, processor on kspDebug. Plain @Preview composables auto-index;
    // the browser root module lives in src/debug (ShowkaseBrowserModule.kt).
    implementation(libs.showkase.annotation)
    debugImplementation(libs.showkase)
    kspDebug(libs.showkase.processor)

    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.material3)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.androidx.compose.ui.test.manifest)
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
}

// Ticket 07: component mode — only @ShowkaseComposable-annotated composables are
// indexed, so the @Preview variants stop multiplying into Light/Dark browser entries.
// The args extension is the shared `ksp { }` block (per-variant `kspDebug` exists only
// as a dependency configuration, there is no kspDebug arg block); harmless for
// release, where this module runs no KSP task carrying the Showkase processor.
ksp {
    arg("requireShowkaseComposableAnnotation", "true")
}

// Screenshot tests default to recording goldens; run with -Proborazzi.verify to
// re-capture and fail on any pixel drift against the committed goldens instead.
val verifySnapshots = providers.gradleProperty("roborazzi.verify").isPresent

tasks.withType<Test>().configureEach {
    systemProperty("roborazzi.test.record", (!verifySnapshots).toString())
    systemProperty("roborazzi.test.verify", verifySnapshots.toString())
}
