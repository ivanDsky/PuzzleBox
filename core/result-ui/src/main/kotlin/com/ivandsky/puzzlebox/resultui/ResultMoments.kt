package com.ivandsky.puzzlebox.resultui

import com.ivandsky.puzzlebox.designsystem.formatDuration
import com.ivandsky.puzzlebox.domain.moments.Moment

private const val MAX_MOMENT_ROWS = 3

/** One row of the moment card: the [moment], its [text] and a [key] that is stable while the row stays. */
internal data class HeroMoment(val key: String, val text: String, val moment: Moment)

/** The rows for [moments]: strongest first (a stable sort), at most three. */
internal fun heroMoments(moments: List<Moment>): List<HeroMoment> =
    moments.distinct().sortedBy { it.strength() }.take(MAX_MOMENT_ROWS).map { HeroMoment(it.rowKey(), it.rowText(), it) }

/** Rank among the kinds, lowest strongest: the order [Moment] declares them in. */
private fun Moment.strength(): Int = when (this) {
    Moment.PersonalBest -> 0
    is Moment.FirstSubX -> 1
    is Moment.TopThreeFriends -> 2
    is Moment.PassedFriend -> 3
    is Moment.FullSetDone -> 4
    is Moment.Streak -> 5
}

private fun Moment.rowKey(): String = when (this) {
    Moment.PersonalBest -> "best"
    is Moment.FirstSubX -> "sub:$thresholdMs"
    is Moment.TopThreeFriends -> "top3"
    is Moment.PassedFriend -> "passed:$uid"
    is Moment.FullSetDone -> "set"
    is Moment.Streak -> "streak"
}

private fun Moment.rowText(): String = when (this) {
    Moment.PersonalBest -> "New personal best"
    is Moment.FirstSubX -> "First sub-${formatDuration(thresholdMs)}"
    is Moment.TopThreeFriends -> "Top 3 among friends today"
    is Moment.PassedFriend -> "You passed $name"
    is Moment.FullSetDone -> fullSetText(total)
    is Moment.Streak -> "$days-day streak"
}

private fun fullSetText(total: Moment.FullSetDone.DayTotal?): String {
    if (total == null) return "Full set done"
    val hints = if (total.hints > 0) ", ${total.hints} ${if (total.hints == 1) "hint" else "hints"}" else ""
    return "Full set done · ${formatDuration(total.timeMs)} total$hints"
}
