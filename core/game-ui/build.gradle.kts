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
    // TodayStatus: todayStatusLine is the one status copy Home and the library share.
    implementation(projects.core.domain)

    implementation(libs.androidx.compose.material3.windowsizeclass)
    implementation(libs.kotlinx.coroutines.core)

    // RecordVerdictTest runs core:testing's shared RankOrderCases table (ADR-0013).
    testImplementation(projects.core.testing)
}
