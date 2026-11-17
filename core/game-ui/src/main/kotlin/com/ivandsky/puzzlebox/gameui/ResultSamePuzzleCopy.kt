package com.ivandsky.puzzlebox.gameui

import com.ivandsky.puzzlebox.designsystem.formatDuration
import com.ivandsky.puzzlebox.game.BestScore
import com.ivandsky.puzzlebox.game.RankOrder

/**
 * The hero line that compares a solve with the player's previous solve of this same puzzle: "12s faster than last time
 * (1:54)", "12s slower than …", "Same time as last time". A LinkedIn previous solve reads "than your LinkedIn time
 * (1:54)". A hints-first game ([order]) words it from hints when the hints differ ("Fewer hints than last time (…)").
 * Times compare at the resolution [formatDuration] shows, in whole seconds.
 */
internal fun heroSamePuzzleLine(
    previous: BestScore,
    fromLinkedIn: Boolean,
    order: RankOrder,
    timeMs: Long,
    hintsUsed: Int,
): String {
    val than = if (fromLinkedIn) "your LinkedIn time" else "last time"
    if (order == RankOrder.HintsThenTime && hintsUsed != previous.hints) {
        val count = if (hintsUsed < previous.hints) "Fewer" else "More"
        return "$count hints than $than (${bestScoreText(previous, order)})"
    }
    if (timeMs / 1000 == previous.timeMs / 1000) {
        return if (fromLinkedIn) "Same as your LinkedIn time (${formatDuration(previous.timeMs)})" else "Same time as last time"
    }
    val direction = if (timeMs < previous.timeMs) "faster" else "slower"
    return "${heroGap(previous.timeMs, timeMs)} $direction than $than (${formatDuration(previous.timeMs)})"
}

/**
 * The second, small line: "Best on this puzzle 1:41", only when the best of the earlier solves is not the previous
 * solve, as [order] shows both; null otherwise.
 */
internal fun heroSamePuzzleBest(solves: SamePuzzleSolves, order: RankOrder): String? {
    val previous = solves.previous ?: return null
    val best = solves.best ?: return null
    if (shownAlike(previous, best, order)) return null
    return "Best on this puzzle ${bestScoreText(best, order)}"
}

private fun shownAlike(a: BestScore, b: BestScore, order: RankOrder): Boolean = when (order) {
    RankOrder.TimeThenHints -> a.timeMs / 1000 == b.timeMs / 1000
    RankOrder.HintsThenTime -> a.hints == b.hints && a.timeMs / 1000 == b.timeMs / 1000
}
