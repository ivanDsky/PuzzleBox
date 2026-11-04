plugins {
    alias(libs.plugins.puzzlebox.android.library.compose)
    alias(libs.plugins.puzzlebox.android.roborazzi)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.ivandsky.puzzlebox.designsystem"

    testOptions {
        unitTests.all { test ->
            test.maxHeapSize = "2g"
        }
    }
}

// Ticket 07: component mode — only @ShowkaseComposable-annotated composables are
// indexed, so the @Preview variants stop multiplying into Light/Dark browser entries.
// The args extension is the shared `ksp { }` block (per-variant `kspDebug` exists only
// as a dependency configuration, there is no kspDebug arg block); harmless for
// release, where this module runs no KSP task carrying the Showkase processor.
ksp {
    arg("requireShowkaseComposableAnnotation", "true")
}

dependencies {
    implementation(libs.androidx.compose.material.icons.core)

    // Showkase trial (spec A9/A10, keep-or-drop decided after the run). Debug-only
    // posture per design-previews-00: browser runtime debugImplementation, annotations
    // in main source, processor on kspDebug. Plain @Preview composables auto-index;
    // the browser root module lives in src/debug (ShowkaseBrowserModule.kt).
    implementation(libs.showkase.annotation)
    debugImplementation(libs.showkase)
    kspDebug(libs.showkase.processor)
}
