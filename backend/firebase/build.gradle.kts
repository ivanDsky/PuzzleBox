plugins {
    alias(libs.plugins.puzzlebox.android.library)
}

android {
    // Unit tests construct real Firebase SDK exceptions, which touch android.* (TextUtils
    // et al.) — covered by the convention's Robolectric + merged-resources test setup.
    namespace = "com.ivandsky.puzzlebox.backend.firebase"
}

dependencies {
    // The contracts this module implements (design §1: the swap unit is this module —
    // swapping backends means re-binding the same interfaces, never editing core).
    implementation(projects.core.data)
    implementation(libs.kotlinx.coroutines.core)
    // Task.await() bridge for the Firebase SDK's ListenableFuture-style Tasks.
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.koin.core)

    testImplementation(projects.core.testing)
    testImplementation(libs.turbine)
    testImplementation(libs.mockito.core)
}
