plugins {
    alias(libs.plugins.puzzlebox.android.feature.impl)
    alias(libs.plugins.puzzlebox.android.roborazzi)
}

android {
    namespace = "com.ivandsky.puzzlebox.play"
}

dependencies {
    implementation(projects.core.navigation)
    implementation(projects.feature.play.api)
    // DuelKey: the type of the onOpenDuel pass-through (api to api edge, ADR-0008).
    implementation(projects.feature.compete.api)
    implementation(projects.core.common)
    implementation(projects.core.data)
    // LoadPastDay: SlotOpening opens a past day through the same use case History uses;
    // ObserveTodaySet: the hub's statuses and Result's "Next".
    implementation(projects.core.domain)
    implementation(projects.core.game)
    // ADR-0001/ADR-0007: play is game-agnostic — every game reaches it through the
    // core:game-ui play seam (GamePlayUi/GamePlayController), never a games:* edge.
    implementation(projects.core.gameUi)
    // A friend puzzle's board needs sign-in: the Google ID token comes from GoogleIdTokenProvider.
    implementation(projects.core.auth)

    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(projects.core.testing)
    // GameRouteTest: system back through a real NavDisplay scene, as the app shell hosts it.
    testImplementation(libs.androidx.navigation3.ui)
}
