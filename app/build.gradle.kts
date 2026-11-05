plugins {
    alias(libs.plugins.puzzlebox.android.application.compose)
    alias(libs.plugins.puzzlebox.android.roborazzi)
    alias(libs.plugins.kotlin.serialization)
    // Ticket 16: merges the root google-services.json into resources (FirebaseInitProvider
    // auto-initializes FirebaseApp; the type-3 web client surfaces as default_web_client_id).
    alias(libs.plugins.google.services)
}

android {
    namespace = "com.ivandsky.puzzlebox.app"

    defaultConfig {
        applicationId = "com.ivandsky.puzzlebox"
        versionCode = 14
        versionName = "0.12.0"
        // Device tests run on a plain Application; each test starts its own E2eKoinGraph.
        testInstrumentationRunner = "com.ivandsky.puzzlebox.app.device.PuzzleBoxTestRunner"
    }

    // The fakes graph and play fixtures serve both the JVM shell tests and the device tests.
    sourceSets {
        getByName("test").kotlin.srcDir("src/sharedTest/kotlin")
        getByName("androidTest").kotlin.srcDir("src/sharedTest/kotlin")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // Local distribution (tailnet test device): unsigned release APKs
            // cannot install; the debug key is the distribution key here.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    testOptions {
        // The ShowkaseBrowserScreenshotTest screenshot suite needs heap headroom.
        unitTests.all { test ->
            test.maxHeapSize = "2g"
            // One graphics mode for every class keeps one Robolectric sandbox per sdk, so the
            // native runtime (and its fonts copy through a JVM-global zip FileSystem) loads
            // once per JVM. Mixed LEGACY/NATIVE sandboxes collided there under load
            // (FileSystemAlreadyExistsException, then UnsatisfiedLinkError; robolectric#10116).
            test.systemProperty("robolectric.graphicsMode", "NATIVE")
        }
    }
}

dependencies {
    implementation(projects.backend.firebase)
    implementation(projects.core.common)
    implementation(projects.core.designsystem)
    implementation(projects.core.data)
    // domainModule's use cases (Home, History and Profile observe through them).
    implementation(projects.core.domain)
    // The records contract (CompletionRepository) speaks core:game types (CompletionRecord).
    implementation(projects.core.game)
    implementation(projects.core.gameUi)
    implementation(projects.core.model)
    implementation(projects.games.zip)
    implementation(projects.games.zipUi)
    implementation(projects.games.queens)
    implementation(projects.games.queensUi)
    implementation(projects.games.tango)
    implementation(projects.games.tangoUi)
    implementation(projects.core.navigation)
    // The shell composes every feature's entries and registers every feature's keys.
    implementation(projects.feature.home.api)
    implementation(projects.feature.home.impl)
    implementation(projects.feature.play.api)
    implementation(projects.feature.play.impl)
    implementation(projects.feature.history.api)
    implementation(projects.feature.history.impl)
    implementation(projects.feature.profile.api)
    implementation(projects.feature.profile.impl)

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3.windowsizeclass)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)

    // Ticket design-previews-08: the debug-only custom Showkase browser compiles
    // against Showkase models; the debug source set never enters release builds.
    debugImplementation(libs.showkase)
    // Robolectric's resources mode reads the app's (binary) debug manifest, and only
    // app-classpath manifest entries land in it, so testImplementation alone would leave
    // createComposeRule's ComponentActivity unresolvable. Debug APK only.
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    // DiGraphTest asserts the Room database resolves from the composed graph.
    testImplementation(projects.core.database)
    testImplementation(projects.core.testing)
    testImplementation(libs.koin.test)
    testImplementation(libs.koin.test.junit4)

    // Device tests (ticket 23): the real MainActivity over the shared fakes graph.
    androidTestImplementation(projects.core.testing)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
}
