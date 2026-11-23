plugins {
    alias(libs.plugins.puzzlebox.android.library.compose)
    alias(libs.plugins.puzzlebox.android.roborazzi)
}

android {
    namespace = "com.ivandsky.puzzlebox.gameui"
}

dependencies {
    implementation(projects.core.game)
    // GameId appears in GamePlayRegistry's/GamePlayUi's public lookup API.
    api(projects.core.model)
    implementation(projects.core.designsystem)

    implementation(libs.kotlinx.coroutines.core)

    // RecordVerdictTest runs core:testing's shared RankOrderCases table (ADR-0013).
    testImplementation(projects.core.testing)
}
