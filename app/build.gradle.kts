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
        // Agent builds pass -PagentVersionCode/-PagentVersionName (deliver build_identity.py); the fallback is the committed release value.
        versionCode = providers.gradleProperty("agentVersionCode").orNull?.toInt() ?: 100
        versionName = providers.gradleProperty("agentVersionName").orNull ?: "0.19.1"
        // Device tests run on a plain Application; each test starts its own E2eKoinGraph.
        testInstrumentationRunner = "com.ivandsky.puzzlebox.app.device.PuzzleBoxTestRunner"
    }

    // The fakes graph and play fixtures serve both the JVM shell tests and the device tests.
    sourceSets {
        getByName("test").kotlin.srcDir("src/sharedTest/kotlin")
        getByName("androidTest").kotlin.srcDir("src/sharedTest/kotlin")
        // LinkedInArchiveE2ETest reads each LinkedIn game's committed by-id fixtures in place:
        // the game module stays their one home, so the E2E and the unit tests never drift.
        listOf("zip", "queens", "tango", "sudoku").forEach { game ->
            getByName("test").resources.srcDir("../games/$game/src/test/resources")
        }
    }

    // The release key lives outside the repo (owner: ~/.gradle/gradle.properties, CI: -P from
    // secrets). Absent properties leave release unsigned; it never falls back to the debug key.
    val releaseSigning = listOf("storeFile", "storePassword", "keyAlias", "keyPassword")
        .associateWith { providers.gradleProperty("puzzlebox.signing.$it").orNull }
    if (releaseSigning.values.all { it != null }) {
        signingConfigs.create("release") {
            storeFile = file(releaseSigning.getValue("storeFile")!!)
            storePassword = releaseSigning.getValue("storePassword")
            keyAlias = releaseSigning.getValue("keyAlias")
            keyPassword = releaseSigning.getValue("keyPassword")
        }
    }

    buildTypes {
        debug {
            // Debug and release install side by side; the debug label is "PuzzleBox Dev".
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.findByName("release")
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

// AGP host-tests only the debug build type by default; ReleaseGameModulesTest needs the
// release source set to prove debug-only games stay out of the release graph (ADR-0015).
androidComponents {
    beforeVariants(selector().withBuildType("release")) { variant ->
        variant.hostTests[com.android.build.api.variant.HostTestBuilder.UNIT_TEST_TYPE]?.enable = true
    }
}

// The release host-test task exists for the src/testRelease tests alone: the src/test suite needs
// debug-only dependencies (ui-test-manifest) and runs under testDebugUnitTest.
tasks.withType<Test>().matching { it.name == "testReleaseUnitTest" }.configureEach {
    filter.includeTestsMatching("com.ivandsky.puzzlebox.app.ReleaseGameModulesTest")
    filter.includeTestsMatching("com.ivandsky.puzzlebox.app.ReleaseUpdateChecksTest")
    filter.includeTestsMatching("com.ivandsky.puzzlebox.app.ReleaseFlagsBindingTest")
    filter.includeTestsMatching("com.ivandsky.puzzlebox.app.ReleaseShareLinksTest")
}

dependencies {
    implementation(projects.backend.firebase)
    implementation(projects.core.auth)
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
    implementation(projects.games.sudoku)
    implementation(projects.games.sudokuUi)
    implementation(projects.games.strands)
    implementation(projects.games.strandsUi)
    implementation(projects.core.navigation)
    // The shell composes every feature's entries and registers every feature's keys.
    implementation(projects.feature.home.api)
    implementation(projects.feature.home.impl)
    implementation(projects.feature.play.api)
    implementation(projects.feature.play.impl)
    implementation(projects.feature.create.api)
    implementation(projects.feature.create.impl)
    implementation(projects.feature.history.api)
    implementation(projects.feature.history.impl)
    implementation(projects.feature.profile.api)
    implementation(projects.feature.profile.impl)
    implementation(projects.feature.compete.api)
    implementation(projects.feature.compete.impl)

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
    // LinkedInArchiveE2ETest: the production LinkedIn transport (linkedInFeedTransport, which
    // takes the app's Json) over a local HTTP server that answers as LinkedIn does (F1
    // handshake, A17 cooldown).
    testImplementation(projects.core.network)
    testImplementation(libs.kotlinx.serialization.json)
    testImplementation(libs.okhttp.mockwebserver)

    // Device tests (ticket 23): the real MainActivity over the shared fakes graph.
    androidTestImplementation(projects.core.testing)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
}
