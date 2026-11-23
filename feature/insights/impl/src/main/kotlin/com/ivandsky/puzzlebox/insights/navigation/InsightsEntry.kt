package com.ivandsky.puzzlebox.insights.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.ivandsky.puzzlebox.insights.api.InsightsKey
import com.ivandsky.puzzlebox.insights.api.InsightsRecapKey
import com.ivandsky.puzzlebox.insights.api.InsightsStatsKey
import com.ivandsky.puzzlebox.insights.hub.InsightsHubRoute
import com.ivandsky.puzzlebox.insights.recap.RecapRoute
import com.ivandsky.puzzlebox.insights.stats.StatsRoute
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.play.api.ReplayKey
import java.time.LocalDate

/**
 * Registers Insights' routes: the hub ([InsightsKey]), a recap story ([InsightsRecapKey]) and the stats page
 * ([InsightsStatsKey]). [onBack] leaves the hub, a recap or the stats page;
 * [onOpenRecap] opens the recap of the period whose id (`YYYY-MM` or `YYYY`) it is given; [onOpenReplay] opens the
 * replay a recap's fastest solve row or a stats record names (the play feature's page, reached through its api key
 * only); [onOpenDay] opens History at a game and day, for a record with no replay. [onOpenStats] is the hub's
 * "Stats for all games" row.
 */
fun EntryProviderScope<NavKey>.insightsEntry(
    onBack: () -> Unit,
    onOpenRecap: (periodId: String) -> Unit,
    onOpenReplay: (ReplayKey) -> Unit,
    onOpenStats: () -> Unit,
    onOpenDay: (GameId, LocalDate) -> Unit,
) {
    entry<InsightsKey> { InsightsHubRoute(onOpenRecap = onOpenRecap, onOpenStats = onOpenStats, onBack = onBack) }
    entry<InsightsRecapKey> { key -> RecapRoute(periodId = key.period, onOpenReplay = onOpenReplay, onClose = onBack) }
    entry<InsightsStatsKey> { key ->
        StatsRoute(gameId = key.gameId, onOpenReplay = onOpenReplay, onOpenDay = onOpenDay, onBack = onBack)
    }
}
