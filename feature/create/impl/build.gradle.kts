plugins {
    alias(libs.plugins.puzzlebox.android.feature.impl)
    alias(libs.plugins.puzzlebox.android.roborazzi)
}

android {
    namespace = "com.ivandsky.puzzlebox.create"
}

dependencies {
    implementation(projects.core.navigation)
    implementation(projects.feature.create.api)
    // Drafts are stored through DraftRepository; new ones come from CreateDraft.
    implementation(projects.core.data)
    implementation(projects.core.domain)
    // ADR-0019: create is game-agnostic — boards come from each game's GameEditor and
    // GameEditorUi through the registries, never a games:* edge.
    implementation(projects.core.game)
    implementation(projects.core.gameUi)
    // Publishing signs in first: the Google ID token comes from GoogleIdTokenProvider.
    implementation(projects.core.auth)

    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(projects.core.testing)
}
