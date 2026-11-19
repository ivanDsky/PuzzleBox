package com.ivandsky.puzzlebox.insights.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.ivandsky.puzzlebox.insights.api.InsightsKey
import com.ivandsky.puzzlebox.insights.api.InsightsRecapKey
import com.ivandsky.puzzlebox.insights.api.InsightsStatsKey
import com.ivandsky.puzzlebox.insights.hub.InsightsHubRoute
import com.ivandsky.puzzlebox.insights.recap.RecapRoute
import com.ivandsky.puzzlebox.play.api.ReplayKey

/**
 * Registers Insights' routes: the hub ([InsightsKey]), a recap story ([InsightsRecapKey]) and the stats page
 * ([InsightsStatsKey]), still an empty page. The routes are ungated; each entry point that opens one is gated by its
 * own [com.ivandsky.puzzlebox.insights.flags.InsightsFlag]. [onBack] leaves the hub or a recap; [onOpenRecap] opens the
 * recap of the period whose id (`YYYY-MM` or `YYYY`) it is given; [onOpenReplay] opens the replay a recap's fastest
 * solve row names (the play feature's page, reached through its api key only).
 */
fun EntryProviderScope<NavKey>.insightsEntry(
    onBack: () -> Unit,
    onOpenRecap: (periodId: String) -> Unit,
    onOpenReplay: (ReplayKey) -> Unit,
) {
    entry<InsightsKey> { InsightsHubRoute(onOpenRecap = onOpenRecap, onBack = onBack) }
    entry<InsightsRecapKey> { key -> RecapRoute(periodId = key.period, onOpenReplay = onOpenReplay, onClose = onBack) }
    entry<InsightsStatsKey> { InsightsPlaceholder() }
}
