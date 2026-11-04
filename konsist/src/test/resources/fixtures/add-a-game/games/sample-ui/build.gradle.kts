plugins {
    alias(libs.plugins.puzzlebox.android.library.compose)
}

android {
    namespace = "com.ivandsky.puzzlebox.games.sampleui"
}

dependencies {
    implementation(projects.games.sample)
    implementation(projects.core.game)
    implementation(projects.core.designsystem)
    implementation(libs.koin.core)
}
