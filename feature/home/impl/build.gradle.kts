plugins {
    alias(libs.plugins.puzzlebox.android.feature.impl)
}

android {
    namespace = "com.ivandsky.puzzlebox.home"
}

dependencies {
    implementation(projects.core.navigation)
    implementation(projects.feature.home.api)
    // Home's affordances push other features' keys (api edges only, never their impls).
    implementation(projects.feature.play.api)
    implementation(projects.feature.history.api)
    implementation(projects.feature.profile.api)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(projects.core.data)
    // HomeViewModel resolves per-game names against the app's GameRegistry.
    implementation(projects.core.game)
    // The domain use cases appear in HomeViewModel's public constructor.
    api(projects.core.domain)
    // GameGlyph, WeekStrip and the per-game brands (GamePlayRegistry) for the Today tiles,
    // streak card and recent chips.
    implementation(projects.core.gameUi)
    // ThemeMode/GameId appear in Home's public composables.
    api(projects.core.model)
    testImplementation(projects.core.testing)
}
