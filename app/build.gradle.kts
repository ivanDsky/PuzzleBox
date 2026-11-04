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
        versionCode = 8
        versionName = "0.8.0"
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
        }
    }
}

dependencies {
    implementation(projects.backend.firebase)
    implementation(projects.core.common)
    implementation(projects.core.designsystem)
    implementation(projects.core.data)
    // domainModule's use cases; ProfileViewModel observes today's board through them.
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
    implementation(projects.feature.home)
    implementation(projects.feature.play)
    implementation(projects.feature.history)

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.material3.windowsizeclass)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)
    // Ticket 16: Credential Manager Google sign-in feeds the backend an ID token.
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services)
    implementation(libs.googleid)

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
    testImplementation(libs.turbine)
    testImplementation(libs.mockito.core)
}
