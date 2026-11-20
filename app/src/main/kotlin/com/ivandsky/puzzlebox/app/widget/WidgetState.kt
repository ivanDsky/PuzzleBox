package com.ivandsky.puzzlebox.app.widget

import com.ivandsky.puzzlebox.domain.TodayStatus
import java.time.Instant

/** Everything one draw of the home-screen widget needs; [nextRollover] is when the next puzzles land, null when unknown. */
data class WidgetState(
    val rings: List<WidgetRing>,
    val streak: WidgetStreak,
    val upNext: WidgetUpNext,
    val nextRollover: Instant?,
) {
    /** Whether the read says anything about today's games: at least one is not offline. Done count and Up next need it. */
    val known: Boolean get() = rings.any { it.status != TodayStatus.Offline }
}
