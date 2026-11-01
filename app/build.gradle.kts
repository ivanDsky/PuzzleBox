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
        versionCode = 4
        versionName = "0.5.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
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
        // Robolectric Compose/resource tests (Profile states, DI graph) read merged resources.
        unitTests {
            isIncludeAndroidResources = true
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
}
