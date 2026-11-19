package com.ivandsky.puzzlebox.app.nav

import com.ivandsky.puzzlebox.common.flags.ReleaseFlagKey
import com.ivandsky.puzzlebox.insights.api.InsightsKey
import com.ivandsky.puzzlebox.insights.api.InsightsStatsKey
import com.ivandsky.puzzlebox.insights.flags.InsightsFlag
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.navigation.Navigator

/**
 * Profile's way into Insights, behind [InsightsFlag.MonthlyRecap] (ADR-0022): while the flag is in [enabled] it opens the
 * hub as a layer page; while it is not, null, and Profile draws no row. The one gate, so no entry point leaks Insights
 * before the flag turns on.
 */
internal fun insightsOpener(enabled: Set<ReleaseFlagKey>, navigator: Navigator): (() -> Unit)? =
    if (InsightsFlag.MonthlyRecap in enabled) ({ navigator.pushLayer(InsightsKey) }) else null

/**
 * The way to the stats page, behind [InsightsFlag.StatsPage] (ADR-0022): while the flag is in [enabled] it opens that
 * game's stats page as a layer page, or the all-games page for a null game; while it is not, null, so the game page's
 * stats card and the hub draw no entry for it.
 */
internal fun statsOpener(enabled: Set<ReleaseFlagKey>, navigator: Navigator): ((GameId?) -> Unit)? =
    if (InsightsFlag.StatsPage in enabled) ({ game -> navigator.pushLayer(InsightsStatsKey(game?.value)) }) else null
