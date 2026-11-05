plugins {
    alias(libs.plugins.puzzlebox.android.library)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.ivandsky.puzzlebox.navigation"
}

dependencies {
    // NavKey (the per-tab stacks' element type) and the SavedStateConfiguration the
    // NavigationState restores through are public API; feature api modules see them here.
    api(libs.androidx.navigation3.runtime)
    // Every feature api's NavKeys are @Serializable and register polymorphically.
    api(libs.kotlinx.serialization.core)
    // Snapshot state and the @Immutable/@Stable runtime annotations. Not `api`, so feature
    // api modules stay Compose-free.
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.runtime)
    // Decode-time invariant checks round-trip the state through a concrete format.
    testImplementation(libs.kotlinx.serialization.json)
}
