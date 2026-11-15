// Macrobenchmark journeys against :app's `benchmark` build type (release-like, profileable,
// com.ivandsky.puzzlebox.benchmark). The module has no dependency on any project module: it drives the
// installed app through UiAutomator, so it never joins the app's module graph.
plugins {
    alias(libs.plugins.puzzlebox.android.benchmark)
}

android {
    namespace = "com.ivandsky.puzzlebox.benchmark"
    targetProjectPath = ":app"
    // The test APK instruments itself and launches the target app from outside its process.
    experimentalProperties["android.experimental.self-instrumenting"] = true

    buildTypes {
        // Pairs with :app's `benchmark` build type (com.ivandsky.puzzlebox.benchmark); there is no
        // other variant to point a journey at, so none of them can reach the release package.
        create("benchmark") {
            isDebuggable = true
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += "release"
        }
    }

    defaultConfig {
        // Not the namespace: the app under test is already com.ivandsky.puzzlebox.benchmark.
        testApplicationId = "com.ivandsky.puzzlebox.macrobenchmark"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
}

// Only the benchmark variant exists: the debug and release variants would pair with :app's debug and
// release builds, the user's real packages, which no journey may install over or drive.
androidComponents {
    beforeVariants { variant -> variant.enable = variant.buildType == "benchmark" }
}

dependencies {
    implementation(libs.androidx.benchmark.macro.junit4)
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.androidx.test.uiautomator)
}
