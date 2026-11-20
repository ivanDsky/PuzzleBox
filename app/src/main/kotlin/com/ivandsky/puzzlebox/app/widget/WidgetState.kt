package com.ivandsky.puzzlebox.app.widget

import java.time.Instant

/** Everything one draw of the home-screen widget needs; [nextRollover] is when the next puzzles land, null when unknown. */
data class WidgetState(
    val rings: List<WidgetRing>,
    val streak: WidgetStreak,
    val upNext: WidgetUpNext,
    val nextRollover: Instant?,
)
