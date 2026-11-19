plugins {
    alias(libs.plugins.puzzlebox.android.feature.impl)
}

android {
    namespace = "com.ivandsky.puzzlebox.insights"
}

dependencies {
    // InsightsKey, InsightsRecapKey, InsightsStatsKey: the keys the entry builder registers routes for.
    implementation(projects.feature.insights.api)
    // InsightsFlag implements ReleaseFlagKey, and the module declares its set with flagSet (ADR-0022).
    implementation(projects.core.common)
}
