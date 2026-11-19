package com.ivandsky.puzzlebox.insights.domain.stats

import com.ivandsky.puzzlebox.insights.domain.recap.FastestSolve
import com.ivandsky.puzzlebox.insights.domain.recap.HintFreeRun
import com.ivandsky.puzzlebox.insights.domain.recap.SolveRef
import com.ivandsky.puzzlebox.insights.domain.recap.StreakRun
import com.ivandsky.puzzlebox.model.GameId

/**
 * The records a range holds (A13), each one with the solve it came from. A record the range has nothing for is absent,
 * never zero: [fastest] lists only games with a counted solve, in the app's game order.
 */
internal data class StatsRecords(
    val fastest: List<Fastest>,
    val longestStreak: Streak?,
    val longestHintFreeRun: HintFreeRun?,
) {

    /** Whether the range has no record at all. */
    val isEmpty: Boolean get() = fastest.isEmpty() && longestStreak == null && longestHintFreeRun == null

    /** [game]'s quickest solve. */
    data class Fastest(val game: GameId, val solve: FastestSolve)

    /** The longest streak [run] and the [solve] that brought it to its length; null when the days inside the range were all frozen. */
    data class Streak(val run: StreakRun, val solve: SolveRef?)
}
