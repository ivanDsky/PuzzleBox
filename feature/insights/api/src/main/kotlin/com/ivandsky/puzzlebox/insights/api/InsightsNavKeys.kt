package com.ivandsky.puzzlebox.insights.api

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.modules.PolymorphicModuleBuilder

/** Registers the Insights feature's keys for back-stack saved state. */
fun PolymorphicModuleBuilder<NavKey>.insightsNavKeys() {
    subclass(InsightsKey::class, InsightsKey.serializer())
    subclass(InsightsRecapKey::class, InsightsRecapKey.serializer())
    subclass(InsightsStatsKey::class, InsightsStatsKey.serializer())
}
