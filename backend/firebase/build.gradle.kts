plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.ivandsky.puzzlebox.backend.firebase"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        // Constructing real Firebase SDK exceptions touches android.* (TextUtils et al.).
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

dependencies {
    // The contracts this module implements (design §1: the swap unit is this module —
    // swapping backends means re-binding the same interfaces, never editing core).
    implementation(project(":core:data"))
    implementation(libs.kotlinx.coroutines.core)
    // Task.await() bridge for the Firebase SDK's ListenableFuture-style Tasks.
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.koin.core)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.mockito.core)
    testImplementation(libs.robolectric)
}
