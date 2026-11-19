plugins {
    alias(libs.plugins.puzzlebox.android.feature.impl)
}

android {
    namespace = "com.ivandsky.puzzlebox.train"
}

dependencies {
    // Module and module { } in di/TrainModule.kt.
    implementation(libs.koin.core)
}
