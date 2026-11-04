plugins {
    alias(libs.plugins.puzzlebox.android.feature)
}

android {
    namespace = "com.ivandsky.puzzlebox.home"
}

dependencies {
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(projects.core.data)
    // F1 module map: FEAT → GAMES is a sanctioned edge (same as :feature:history/play).
    // HomeRoute consumes the aggregated plugin list (GamePlugin) to build the featured
    // game's identity, so the edge is a compile dependency, not test-only.
    implementation(projects.core.game)
    // ThemeMode/GameId appear in Home's public composables.
    api(projects.core.model)
}
