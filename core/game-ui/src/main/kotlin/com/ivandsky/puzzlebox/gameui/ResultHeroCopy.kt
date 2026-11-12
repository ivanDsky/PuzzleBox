package com.ivandsky.puzzlebox.gameui

import com.ivandsky.puzzlebox.designsystem.formatDuration
import com.ivandsky.puzzlebox.domain.SpreadScope
import com.ivandsky.puzzlebox.domain.TimeSpread
import com.ivandsky.puzzlebox.game.BestScore
import com.ivandsky.puzzlebox.game.RankOrder
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale
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
    RecordVerdict.NotBest, RecordVerdict.PlayAgain, null -> null
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
 * an unknown verdict, or a verdict with no best to name). A play-again names the best it did not
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
        RecordVerdict.PlayAgain -> best?.let { "Replay · Best ${bestScoreText(it, order)}" } ?: "Replay"
        RecordVerdict.TiedBest, null -> null
    }
}

/** The streak card's title: "7 day streak". */
internal fun heroStreakTitle(days: Int): String = "$days day streak"

/** The streak card's sub line: day one invites a return; later days name the next number. */
internal fun heroStreakSub(days: Int): String =
    if (days <= 1) "Day one. Come back tomorrow to keep it alive." else "Solve any puzzle tomorrow to make it ${days + 1}."

/** A sentence with one stretch to draw bold. */
internal data class Emphasized(val text: String, val bold: IntRange?)

/**
 * The histogram heading in three runs: the chip, the scope word, sits between [prefix] and [suffix].
 * Without a chip (the scopes hold the same solves) the whole heading is [prefix].
 */
internal data class HeroHeading(val prefix: String, val chip: String?, val suffix: String) {
    val text: String get() = prefix + chip.orEmpty() + suffix
}

/**
 * The heading over [solves] solves in [scope]: "Your 5 [Monday ⇄] Zip times", or "Your first
 * [Monday ⇄] Zip time". The chip word is the weekday name, or "any day" for every day. With no
 * chip ([switchable] false) it reads "Your 5 Zip times".
 */
internal fun heroSpreadHeading(scope: SpreadScope, solves: Int, game: String, weekday: String, switchable: Boolean): HeroHeading {
    val count = if (solves <= 1) "first" else "$solves"
    val noun = if (solves <= 1) "time" else "times"
    if (!switchable) return HeroHeading("Your $count $game $noun", null, "")
    val word = if (scope == SpreadScope.Weekday) weekday else "any day"
    return HeroHeading("Your $count ", word, " $game $noun")
}

private const val SWITCH_HINT = "Tap the highlighted word to switch."

/**
 * The sentence under the heading: what a bar counts in [scope], and how to switch ([switchable]
 * false: no chip, so no hint). [first] is a first solve in scope.
 */
internal fun heroSpreadCaption(scope: SpreadScope, first: Boolean, weekday: String, switchable: Boolean): String {
    val body = when {
        scope == SpreadScope.All && !first ->
            "Each bar counts how many of your solves landed in that time range, across every day of the week. " +
                "Taller means more solves."
        scope == SpreadScope.All ->
            "The one bar is today's solve. Each bar counts solves in a time range — your own solves only, " +
                "no other players."
        !first ->
            "Same chart, ${weekday}s only. Daily puzzles get easier or harder through the week, so a $weekday " +
                "is compared with your other ${weekday}s."
        else ->
            "The one bar is today's $weekday. Play more ${weekday}s and the bars show how your $weekday times " +
                "spread out."
    }
    return if (switchable) "$body $SWITCH_HINT" else body
}

private const val PLAY_AGAIN_NOTE = " Dashed = this replay, not saved."

/**
 * The line under the chart: a first solve invites another; the best says so in bold; otherwise
 * the rank in bold. Then the average of the earlier solves. A hints-first game says "best" for "fastest".
 */
internal fun heroSpreadVerdict(
    scope: SpreadScope,
    order: RankOrder,
    spread: TimeSpread,
    game: String,
    weekday: String,
    playAgain: Boolean,
): Emphasized {
    val playAgainNote = if (playAgain) PLAY_AGAIN_NOTE else ""
    val weekdayScope = scope == SpreadScope.Weekday
    if (spread.isFirstSolve) {
        if (!weekdayScope) {
            return Emphasized("Your first $game time — solve again and the bars show how your times spread out.$playAgainNote", null)
        }
        val lead = "First $weekday"
        return Emphasized("$lead — no earlier $weekday $game to compare with yet.$playAgainNote", lead.indices)
    }
    val quality = if (order == RankOrder.TimeThenHints) "fastest" else "best"
    val lead = when {
        spread.best && weekdayScope -> "${quality.replaceFirstChar { it.uppercase() }} $weekday"
        spread.best -> "${quality.replaceFirstChar { it.uppercase() }} ever"
        else -> "${ordinal(spread.rank)} $quality"
    }
    val of = if (weekdayScope) "${spread.solves} $weekday $game" else "${spread.solves} $game"
    val average = spread.averageEarlierMs?.let {
        val label = if (weekdayScope) "$weekday average" else "Average"
        " $label ${formatDuration(it)}."
    }.orEmpty()
    return Emphasized("$lead of your $of.$average$playAgainNote", lead.indices)
}

/** "Rank counts hints first…" for a hints-first game over several solves; else null. */
internal fun heroRankNote(order: RankOrder, spread: TimeSpread): Emphasized? =
    if (order == RankOrder.HintsThenTime && !spread.isFirstSolve) {
        Emphasized("Rank counts hints first, then time — the bars show time only.", null)
    } else {
        null
    }

private const val COMPARE_MIN_EARLIER = 2

/**
 * "Mondays run faster for you — average 1:30 vs 1:53 across all days." once both scopes have two
 * earlier solves and the averages differ; the fastest weekday says so in bold instead. Else null.
 */
internal fun heroWeekdayNote(all: TimeSpread, weekday: TimeSpread, name: String, fastest: Boolean): Emphasized? {
    val allAverage = all.averageEarlierMs
    val dayAverage = weekday.averageEarlierMs
    if (allAverage == null || dayAverage == null) return null
    if (all.solves - 1 < COMPARE_MIN_EARLIER || weekday.solves - 1 < COMPARE_MIN_EARLIER) return null
    if (dayAverage == allAverage) return null
    val tail = "average ${formatDuration(dayAverage)} vs ${formatDuration(allAverage)} across all days."
    if (fastest) {
        val lead = "${name}s are your fastest day"
        return Emphasized("$lead — $tail", lead.indices)
    }
    val direction = if (dayAverage < allAverage) "faster" else "slower"
    return Emphasized("${name}s run $direction for you — $tail", null)
}

/** The weekday's name in [locale]. */
internal fun heroWeekdayName(day: DayOfWeek, locale: Locale): String = day.getDisplayName(TextStyle.FULL, locale)

/** "1:00–1:30": one bin's time range, en dash. */
internal fun heroBinRange(fromMs: Long, toMs: Long): String = "${formatDuration(fromMs)}–${formatDuration(toMs)}"

/** The chart's spoken description: "{heading}. {n} time ranges, counts per range. Today m:ss." */
internal fun heroChartDescription(heading: String, bins: Int, timeMs: Long): String {
    val ranges = if (bins == 1) "1 time range" else "$bins time ranges"
    return "$heading. $ranges, counts per range. Today ${formatDuration(timeMs)}."
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
