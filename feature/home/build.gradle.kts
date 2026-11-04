plugins {
    alias(libs.plugins.puzzlebox.android.feature)
}

android {
    namespace = "com.ivandsky.puzzlebox.home"
}

dependencies {
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(projects.core.data)
    // HomeViewModel resolves per-game names against the app's GameRegistry.
    implementation(projects.core.game)
    // GameSummary (the featured game) appears in HomeScreen's public signature.
    api(projects.core.domain)
    // ThemeMode/GameId appear in Home's public composables.
    api(projects.core.model)
    testImplementation(projects.core.testing)
}
