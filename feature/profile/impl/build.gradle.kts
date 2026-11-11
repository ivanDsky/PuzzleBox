plugins {
    alias(libs.plugins.puzzlebox.android.feature.impl)
}

android {
    namespace = "com.ivandsky.puzzlebox.profile"
}

dependencies {
    implementation(projects.feature.profile.api)
    // IdentityService in the VM/screen.
    implementation(projects.core.data)
    // ObserveGameStats (the header's "Playing since").
    implementation(projects.core.domain)
    // PlayerAvatar for the header.
    implementation(projects.core.gameUi)
    // GoogleIdTokenProvider: the sign-in seam the VM asks for a token.
    implementation(projects.core.auth)
    // DispatcherProvider for the LinkedIn session.
    implementation(projects.core.common)

    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.koin.android)

    testImplementation(projects.core.testing)
    testImplementation(libs.mockito.core)
}
