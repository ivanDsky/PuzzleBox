plugins {
    alias(libs.plugins.puzzlebox.android.feature.impl)
    // The Train list and practice board renders (review evidence, never committed: ADR-0033).
    alias(libs.plugins.puzzlebox.android.roborazzi)
}

android {
    namespace = "com.ivandsky.puzzlebox.train"
}

dependencies {
    // TrainerProgressRepository, TrainerPuzzleSource and UserPreferencesRepository, which the view models read.
    implementation(projects.core.data)
    // GameRegistry's display names for the game row, and GameId through core:data's api edge.
    implementation(projects.core.game)
    // TrainerRegistry, TrainerGame and PracticeBoard (the seam every game's trainer sits behind), GameGlyph and the play registry's brands.
    implementation(projects.core.gameUi)
    // DispatcherProvider: the practice board is cut on the default dispatcher (P2).
    implementation(projects.core.common)

    // collectAsStateWithLifecycle in the hosts.
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.kotlinx.coroutines.core)
    // koinViewModel() and the view-model bindings in di/TrainModule.kt.
    implementation(libs.koin.android)

    testImplementation(projects.core.testing)
}
