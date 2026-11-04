plugins {
    alias(libs.plugins.puzzlebox.android.library)
}

android {
    namespace = "com.ivandsky.puzzlebox.common"
}

dependencies {
    api(libs.kotlinx.coroutines.core)
    // SerializationException (AppError.Parsing cause) lives in serialization-core.
    implementation(libs.kotlinx.serialization.core)
    implementation(libs.koin.core)
}
