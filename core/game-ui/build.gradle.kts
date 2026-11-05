plugins {
    alias(libs.plugins.puzzlebox.android.library.compose)
}

android {
    namespace = "com.ivandsky.puzzlebox.gameui"
}

dependencies {
    implementation(projects.core.game)
    // GameId appears in GamePlayRegistry's/GamePlayUi's public lookup API.
    api(projects.core.model)
    implementation(projects.core.designsystem)
    implementation(projects.core.common)

    implementation(libs.androidx.compose.material3.windowsizeclass)
    // The chrome's "How to play" icon (core set only; libs.versions.toml icon policy).
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.kotlinx.coroutines.core)

    // RecordVerdictTest runs core:testing's shared RankOrderCases table (ADR-0013).
    testImplementation(projects.core.testing)
}
