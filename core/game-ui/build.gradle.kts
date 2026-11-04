plugins {
    alias(libs.plugins.puzzlebox.android.library.compose)
}

android {
    namespace = "com.ivandsky.puzzlebox.gameui"
}

dependencies {
    implementation(projects.core.game)
    // GameId appears in GameUiRegistry's public registration/lookup API.
    api(projects.core.model)
    implementation(projects.core.designsystem)
    implementation(projects.core.common)

    implementation(libs.androidx.compose.material3.windowsizeclass)
    implementation(libs.kotlinx.coroutines.core)
}
