package com.ivandsky.puzzlebox.app.nav

import com.ivandsky.puzzlebox.common.flags.ReleaseFlagKey
import com.ivandsky.puzzlebox.insights.api.InsightsStatsKey
import com.ivandsky.puzzlebox.insights.flags.InsightsFlag
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.navigation.Navigator

/**
 * The way to the stats page, behind [InsightsFlag.StatsPage] (ADR-0022): while the flag is in [enabled] it opens that
 * game's stats page as a layer page, or the all-games page for a null game; while it is not, null, so the game page's
 * stats card and the hub draw no entry for it.
 */
internal fun statsOpener(enabled: Set<ReleaseFlagKey>, navigator: Navigator): ((GameId?) -> Unit)? =
    if (InsightsFlag.StatsPage in enabled) ({ game -> navigator.pushLayer(InsightsStatsKey(game?.value)) }) else null
