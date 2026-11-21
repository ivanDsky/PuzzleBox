package com.ivandsky.puzzlebox.insights.recap

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import com.ivandsky.puzzlebox.designsystem.Calendar
import com.ivandsky.puzzlebox.designsystem.Moon
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons
import com.ivandsky.puzzlebox.designsystem.formatDuration
import com.ivandsky.puzzlebox.insights.domain.recap.EarnedMoment
import com.ivandsky.puzzlebox.insights.domain.recap.MonthHighlight
import com.ivandsky.puzzlebox.model.GameId

// The words a month card and its fact-list block say about a month's highlight and its stamps.

/** The highlight's headline: "Personal best in Queens", "7-day streak", "A quiet month". */
internal fun MonthHighlight.title(gameName: (GameId) -> String): String = when (this) {
    is MonthHighlight.PersonalBest -> "Personal best in ${gameName(game)}"
    is MonthHighlight.FirstSubX -> "First under ${formatDuration(thresholdMs)} in ${gameName(game)}"
    is MonthHighlight.Streak -> "$days-day streak"
    is MonthHighlight.FullSets -> if (count == 1) "Full set done" else "$count full sets"
    is MonthHighlight.UnderAverage -> "${gameName(game)} ran $percent% under your average"
    is MonthHighlight.BusiestMonth -> "Your busiest month for ${gameName(game)}"
    is MonthHighlight.BusiestDay -> "Your busiest day"
    is MonthHighlight.QuietMonth -> "A quiet month"
    is MonthHighlight.Focus -> if (solved == of) "All ${gameName(game)}" else "Mostly ${gameName(game)}"
}

/** The line under the headline: "0:11 · fastest all year", "7 days in a row", "4 puzzles, mostly Zip". */
internal fun MonthHighlight.detail(gameName: (GameId) -> String): String = when (this) {
    is MonthHighlight.PersonalBest -> when {
        timeMs != null -> formatDuration(timeMs) + if (fastestAllYear) " · fastest all year" else ""
        fastestAllYear -> "Fastest all year"
        else -> ""
    }
    is MonthHighlight.FirstSubX -> "A new time milestone"
    is MonthHighlight.Streak -> "$days days in a row"
    is MonthHighlight.FullSets -> if (count == 1) "Every puzzle of the day" else "Every puzzle of the day, $count times"
    is MonthHighlight.UnderAverage -> "Best ${formatDuration(bestMs)} against ${formatDuration(averageMs)}"
    is MonthHighlight.BusiestMonth -> "$solved ${solved.puzzles()} of ${gameName(game)}"
    is MonthHighlight.BusiestDay -> "$solved ${solved.puzzles()} that day"
    is MonthHighlight.QuietMonth -> "$solved ${solved.puzzles()}, mostly ${gameName(mostlyGame)}"
    is MonthHighlight.Focus -> "$solved of $of puzzles"
}

/** The highlight's short value at the end of its fact-list row: the time, "<1:00", "7d", "×3" or a count. */
internal fun MonthHighlight.value(): String = when (this) {
    is MonthHighlight.PersonalBest -> timeMs?.let(::formatDuration).orEmpty()
    is MonthHighlight.FirstSubX -> "<${formatDuration(thresholdMs)}"
    is MonthHighlight.Streak -> "${days}d"
    is MonthHighlight.FullSets -> "×$count"
    is MonthHighlight.UnderAverage -> formatDuration(bestMs)
    is MonthHighlight.BusiestMonth -> "$solved"
    is MonthHighlight.BusiestDay -> "$solved×"
    is MonthHighlight.QuietMonth -> "$solved"
    is MonthHighlight.Focus -> "$solved"
}

/** The glyph a highlight with no game carries: a trophy, a clock, a box, a flame, a moon or a calendar. */
internal val MonthHighlight.kindIcon: ImageVector
    @Composable get() = when (this) {
        is MonthHighlight.PersonalBest -> PuzzleIcons.Trophy
        is MonthHighlight.FirstSubX, is MonthHighlight.UnderAverage -> PuzzleIcons.Clock
        is MonthHighlight.FullSets -> PuzzleIcons.Box
        is MonthHighlight.Streak -> PuzzleIcons.Flame
        is MonthHighlight.QuietMonth -> PuzzleIcons.Moon
        is MonthHighlight.BusiestDay, is MonthHighlight.BusiestMonth, is MonthHighlight.Focus -> PuzzleIcons.Calendar
    }

/** The highlight's glyph: its game's when it is about one game, else [kindIcon]. */
internal val MonthHighlight.glyph: ImageVector
    @Composable get() = game?.let { LocalGameBrands.current(it).glyph } ?: kindIcon

/** "2 personal bests · 1 time milestone · 1 full set": each kind the month earned, counted, in the Moments card's order; "No stamps this month" with none. */
internal fun stampsSummary(moments: List<EarnedMoment>): String {
    val counts = moments.groupingBy { it.kind }.eachCount()
    if (counts.isEmpty()) return NoStamps
    return EarnedMoment.Kind.entries.mapNotNull { kind ->
        val n = counts[kind] ?: return@mapNotNull null
        val (one, many) = when (kind) {
            EarnedMoment.Kind.PersonalBest -> "personal best" to "personal bests"
            EarnedMoment.Kind.FirstSubX -> "time milestone" to "time milestones"
            EarnedMoment.Kind.FullSet -> "full set" to "full sets"
            EarnedMoment.Kind.Streak -> "streak milestone" to "streak milestones"
        }
        "$n ${if (n == 1) one else many}"
    }.joinToString(" · ")
}

/** What a month with no stamps says on its board and in its fact-list row. */
internal const val NoStamps = "No stamps this month"
