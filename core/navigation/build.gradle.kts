plugins {
    alias(libs.plugins.puzzlebox.android.library)
}

android {
    namespace = "com.ivandsky.puzzlebox.navigation"
}

dependencies {
    // NavKey/NavBackStack and the SavedStateConfiguration the back stack restores through
    // are Navigator's public API; feature api modules see them through this module.
    api(libs.androidx.navigation3.runtime)
    // Every feature api's NavKeys are @Serializable and register polymorphically.
    api(libs.kotlinx.serialization.core)
}
