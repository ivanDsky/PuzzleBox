plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.ivandsky.puzzlebox.common"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    api(libs.kotlinx.coroutines.core)
    // SerializationException (AppError.Parsing cause) lives in serialization-core.
    implementation(libs.kotlinx.serialization.core)
    implementation(libs.koin.core)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
