plugins {
    alias(libs.plugins.puzzlebox.android.feature.impl)
    // 30's DuelsTab goldens.
    alias(libs.plugins.puzzlebox.android.roborazzi)
}

android {
    namespace = "com.ivandsky.puzzlebox.compete"
}

dependencies {
    // DuelKey and DuelRevealKey (I2) and the entry keys the navigation extension registers.
    implementation(projects.feature.compete.api)
    // GoogleIdTokenProvider: a signed-out player signs in from the New duel sheet (31).
    implementation(projects.core.auth)
    // MatchRepository, MatchResultOutbox and the match models the Duels screens observe (30 onward).
    implementation(projects.core.data)
    // ObserveMyDuels, ComputeMatchOutcome and the other use cases the view models call (30 onward).
    implementation(projects.core.domain)
    // RankOrder in DuelCompare's public signature (19b) and the leg game ids (36).
    implementation(projects.core.game)
    // PlayerAvatar, DuelCompare and the shared game-ui kit (36).
    implementation(projects.core.gameUi)

    // collectAsStateWithLifecycle in the Duels tab and reveal screens (30).
    implementation(libs.androidx.lifecycle.runtime.compose)
    // Flow collection and the reveal machine's ticker scope (30).
    implementation(libs.kotlinx.coroutines.core)
    // koinViewModel() and the view-model bindings in di/CompeteModule.kt (30).
    implementation(libs.koin.android)

    testImplementation(projects.core.testing)
}
