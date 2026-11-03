plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    // Ticket 16: merges the root google-services.json into resources (FirebaseInitProvider
    // auto-initializes FirebaseApp; the type-3 web client surfaces as default_web_client_id).
    alias(libs.plugins.google.services)
}

android {
    namespace = "com.ivandsky.puzzlebox.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.ivandsky.puzzlebox"
        minSdk = 26
        targetSdk = 37
        versionCode = 6
        versionName = "0.6.0"
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

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        // Robolectric Compose/resource tests (Profile states, DI graph) and the
        // ShowkaseBrowserScreenshotTest screenshot suite read merged resources; the
        // browser suite needs heap headroom (2g).
        unitTests {
            isIncludeAndroidResources = true
            all { test ->
                test.maxHeapSize = "2g"
            }
        }
    }
}

dependencies {
    implementation(project(":backend:firebase"))
    implementation(project(":core:common"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:data"))
    // The records contract (CompletionRepository) speaks core:game types (CompletionRecord).
    implementation(project(":core:game"))
    implementation(project(":core:game-ui"))
    implementation(project(":core:model"))
    implementation(project(":games:zip"))
    implementation(project(":games:zip-ui"))
    implementation(project(":games:tango"))
    implementation(project(":games:tango-ui"))
    implementation(project(":feature:home"))
    implementation(project(":feature:play"))
    implementation(project(":feature:history"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material3.windowsizeclass)
    implementation(libs.androidx.compose.ui.tooling.preview)
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
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.koin.test)
    testImplementation(libs.koin.test.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.mockito.core)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.material3)
    testImplementation(libs.androidx.compose.material.icons.core)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.androidx.compose.ui.test.manifest)

    // Screenshot tests for the debug-only browser (showkase-v2-05): same Roborazzi
    // stack as :core:designsystem. ui-test-manifest ALSO sits on the DEBUG APP
    // classpath — Robolectric's resources mode reads the app's (binary) debug
    // manifest, and only app-classpath manifest entries land in it, so
    // testImplementation alone would leave createComposeRule's ComponentActivity
    // unresolvable. Debug APK only; release unaffected.
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
}
