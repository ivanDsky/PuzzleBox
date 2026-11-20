package com.ivandsky.puzzlebox.app.widget

import com.ivandsky.puzzlebox.model.GameId
import java.time.Instant

/**
 * What the widget reads beyond Today's set and the stats. A game absent from [bestMs] has no completion; absent from
 * [ranks] it has no rank to show ([WidgetRank.Missing]); [newBest] holds the games whose solve today is a new best.
 */
data class WidgetScoreboard(
    val bestMs: Map<GameId, Long>,
    val ranks: Map<GameId, WidgetRank>,
    val newBest: Set<GameId>,
    val nextRollover: Instant?,
)
