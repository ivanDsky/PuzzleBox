package com.ivandsky.puzzlebox.insights.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.ivandsky.puzzlebox.insights.api.InsightsKey
import com.ivandsky.puzzlebox.insights.api.InsightsRecapKey
import com.ivandsky.puzzlebox.insights.api.InsightsStatsKey

/**
 * Registers Insights' routes: the hub ([InsightsKey]), a recap story ([InsightsRecapKey]) and the stats page
 * ([InsightsStatsKey]). The routes are ungated; each entry point that opens one is gated by its own
 * [com.ivandsky.puzzlebox.insights.flags.InsightsFlag].
 */
fun EntryProviderScope<NavKey>.insightsEntry() {
    entry<InsightsKey> { InsightsPlaceholder() }
    entry<InsightsRecapKey> { InsightsPlaceholder() }
    entry<InsightsStatsKey> { InsightsPlaceholder() }
}
