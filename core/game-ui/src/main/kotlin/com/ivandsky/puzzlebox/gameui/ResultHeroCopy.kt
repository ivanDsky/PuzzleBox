package com.ivandsky.puzzlebox.gameui

import com.ivandsky.puzzlebox.designsystem.formatDuration
import com.ivandsky.puzzlebox.game.BestScore
import com.ivandsky.puzzlebox.game.RankOrder
import kotlin.math.abs

/**
 * Every sentence the Results hero page shows, one pure function each, so the strings live
 * (and are tested) in one place and the screens only draw. Times compare at the resolution
 * [formatDuration] shows, in whole seconds, as [RecordVerdict.of] does.
 */

/** The pill: "New best", "Tied your best!" or "First solve"; null for the other verdicts. */
internal fun heroPill(verdict: RecordVerdict?): String? = when (verdict) {
    RecordVerdict.NewBest -> "New best"
    RecordVerdict.TiedBest -> "Tied your best!"
    RecordVerdict.FirstSolve -> "First solve"
    RecordVerdict.NotBest, RecordVerdict.Replay, null -> null
}

/** The seconds between two times as shown: "12s", or m:ss from 60 seconds ("1:05"). */
internal fun heroGap(fromMs: Long, toMs: Long): String {
    val seconds = abs(fromMs / 1000 - toMs / 1000)
    return if (seconds < 60) "${seconds}s" else formatDuration(seconds * 1000)
}

/** A hint count as the score shows it: "Perfect", "1 hint" or "N hints". */
internal fun heroHints(hints: Int): String = when (hints) {
    0 -> "Perfect"
    1 -> "1 hint"
    else -> "$hints hints"
}

/**
 * The one-sentence comparison under the hero time; null when there is nothing to say (a tie,
 * an unknown verdict, or a verdict with no best to name). A replay names the best it did not
 * move. A hints-first game ([order]) words it from hints, falling to the time gap only when
 * the hints are level.
 */
internal fun heroBestLine(
    verdict: RecordVerdict?,
    previousBest: BestScore?,
    order: RankOrder,
    timeMs: Long,
    hintsUsed: Int,
): String? {
    val best = previousBest
    return when (verdict) {
        RecordVerdict.FirstSolve -> "First solve — this is your time to beat"
        RecordVerdict.NewBest -> best?.let {
            if (order == RankOrder.HintsThenTime && hintsUsed < it.hints) {
                "Fewer hints than your best (${bestScoreText(it, order)})"
            } else {
                "${heroGap(it.timeMs, timeMs)} faster than your best (${formatDuration(it.timeMs)})"
            }
        }
        RecordVerdict.NotBest -> best?.let {
            when (order) {
                RankOrder.TimeThenHints -> "Your best is ${formatDuration(it.timeMs)} · ${heroGap(timeMs, it.timeMs)} off"
                RankOrder.HintsThenTime ->
                    if (it.hints == 0) "Your best is Perfect" else "Your best is ${bestScoreText(it, order)}"
            }
        }
        RecordVerdict.Replay -> best?.let { "Replay · Best ${bestScoreText(it, order)}" } ?: "Replay"
        RecordVerdict.TiedBest, null -> null
    }
}

/** The streak card's title: "7 day streak". */
internal fun heroStreakTitle(days: Int): String = "$days day streak"

/** The streak card's sub line: day one invites a return; later days name the next number. */
internal fun heroStreakSub(days: Int): String =
    if (days <= 1) "Day one. Come back tomorrow to keep it alive." else "Solve any puzzle tomorrow to make it ${days + 1}."

/** The histogram's heading: "Your times · Zip". */
internal fun heroSpreadHeading(game: String): String = "Your times · $game"

/** What the histogram says when there is nothing to spread yet. */
internal const val HERO_FIRST_TIME_LINE = "This is your first time — solve again to see how you spread out."

/** A sentence with one stretch to draw bold. */
internal data class Emphasized(val text: String, val bold: IntRange?)

/**
 * The histogram caption over [solves] solves (this one included): the fastest or best yet
 * ("Your fastest ever of your M solves." / "Your best yet of your M solves." for a
 * hints-first game) when [best]; else "Faster than **N** of your M solves. Average m:ss."
 * ("Better than" for a hints-first game). [averageMs] is the earlier solves' average.
 */
internal fun heroSpreadCaption(
    order: RankOrder,
    best: Boolean,
    beaten: Int,
    solves: Int,
    averageMs: Long?,
): Emphasized {
    if (best) {
        val lead = if (order == RankOrder.TimeThenHints) "Your fastest ever" else "Your best yet"
        return Emphasized("$lead of your $solves solves.", 0 until lead.length)
    }
    val lead = if (order == RankOrder.TimeThenHints) "Faster than " else "Better than "
    val count = beaten.toString()
    val average = averageMs?.let { " Average ${formatDuration(it)}." }.orEmpty()
    return Emphasized("$lead$count of your $solves solves.$average", lead.length until lead.length + count.length)
}

/** The rank row for a signed-out player. */
internal const val HERO_SIGN_IN_LINE = "Sign in to see where you rank today."

/** Below this many players "Top N%" says little, so the row names the place instead. */
internal const val RANK_PERCENT_MIN_PLAYERS = 10

/** The rank row's bold value: "Top 5%", or "#3" while the board is small. */
internal fun heroRankValue(position: Int, count: Int, topPercent: Int): String =
    if (count >= RANK_PERCENT_MIN_PLAYERS) "Top $topPercent%" else "#$position"

/** The rank row's sub line: "of players today", naming the board's size for a place. */
internal fun heroRankSub(count: Int): String =
    if (count >= RANK_PERCENT_MIN_PLAYERS) "of players today" else "of $count players today"

/** The rank row's line for a place among players and LinkedIn connections: "3rd of 12 with connections". */
internal fun heroRankConnections(position: Int, count: Int): String = "${ordinal(position)} of $count with connections"

private fun ordinal(n: Int): String {
    val suffix = if (n % 100 in 11..13) "th" else when (n % 10) {
        1 -> "st"
        2 -> "nd"
        3 -> "rd"
        else -> "th"
    }
    return "$n$suffix"
}
