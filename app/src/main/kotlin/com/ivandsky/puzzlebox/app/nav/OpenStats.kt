package com.ivandsky.puzzlebox.app.nav

import com.ivandsky.puzzlebox.insights.api.InsightsStatsKey
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.navigation.Navigator

/** The way to the stats page: opens that game's stats page as a layer page, or the all-games page for a null game. */
internal fun openStats(navigator: Navigator): (GameId?) -> Unit =
    { game -> navigator.pushLayer(InsightsStatsKey(game?.value)) }
