plugins {
    alias(libs.plugins.puzzlebox.android.feature)
}

android {
    namespace = "com.ivandsky.puzzlebox.history"
}

dependencies {
    implementation(projects.core.data)
    // The A16/A19 decode gate: a replay only launches from a payload the engine trusts.
    implementation(projects.core.game)
    // F1 module map: FEAT → GAMES is a sanctioned edge (same as :feature:play) — history
    // packs replay envelopes (ADR-0005) with the plugins' public codecs, never engine
    // internals.
    implementation(projects.games.zip)
    implementation(projects.games.tango)

    implementation(libs.androidx.paging.compose)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.androidx.paging.testing)
}
