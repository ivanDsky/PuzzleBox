plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.ivandsky.puzzlebox.history"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

dependencies {
    implementation(project(":core:data"))
    implementation(project(":core:designsystem"))
    // The A16/A19 decode gate: a replay only launches from a payload the engine trusts.
    implementation(project(":core:game"))
    // F1 module map: FEAT → GAMES is a sanctioned edge, same as :feature:play — the
    // replay payload is a ZipSession carrying this day's ref, built from the plugin's
    // public codecs, never engine internals.
    implementation(project(":games:zip"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material3.windowsizeclass)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.paging.compose)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.koin.androidx.compose)

    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.material3)
    testImplementation(libs.androidx.compose.material.icons.core)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.androidx.compose.ui.test.manifest)
    testImplementation(libs.androidx.paging.testing)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
}
