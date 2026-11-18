package com.ivandsky.puzzlebox.home

import com.ivandsky.puzzlebox.domain.ResultRank
import com.ivandsky.puzzlebox.designsystem.formatDuration
import com.ivandsky.puzzlebox.gameui.GameBrand
import com.ivandsky.puzzlebox.model.GameId
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/**
 * One puzzle day in the recent list: [isoDay] is its unique key, [games] every registered game in
 * registry order, each with how that day went (unregistered games with a row follow, last).
 */
internal data class HistoryDay(val isoDay: String, val headline: String, val games: List<GameResult>) {
    /** One game's day: [outcome] is solved (with its time, hints and place), opened, or not played. */
    data class GameResult(val gameId: GameId, val gameName: String, val brand: GameBrand, val outcome: Outcome)

    sealed interface Outcome {
        /** Solved in [timeMs] with [hints] used; [standing] is the player's place that day, null while unknown. */
        data class Solved(val timeMs: Long, val hints: Int, val standing: Standing? = null) : Outcome

        /** Opened (its save or captured puzzle exists) but not solved. */
        data object Opened : Outcome

        data object NotPlayed : Outcome
    }

    /** The player's [place] among [count]: their friends when [amongFriends], else the whole board. */
    data class Standing(val place: Int, val count: Int, val amongFriends: Boolean)

    /** Games opened or solved that day: what the Recent row's chips show. */
    val played: List<GameResult> get() = games.filter { it.outcome != Outcome.NotPlayed }

    private val solved: List<Outcome.Solved> get() = games.mapNotNull { it.outcome as? Outcome.Solved }

    val solvedCount: Int get() = solved.size

    val totalTime: String get() = formatDuration(solved.sumOf { it.timeMs })

    val hintsUsed: Int get() = solved.sumOf { it.hints }

    val weekday: String get() = LocalDate.parse(isoDay).dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
}

/** The pane header's muted line: "Sunday · 5 of 5 · 6:57 · no hints" (the time and hints parts only once a game is solved). */
internal fun HistoryDay.summaryLine(): String = buildList {
    add(weekday)
    add("$solvedCount of ${games.size}")
    if (solvedCount > 0) {
        add(totalTime)
        add(hintsText(hintsUsed, capitalised = false))
    }
}.joinToString(" · ")

/** "No hints" / "1 hint" / "4 hints"; the header says "no hints" in running text. */
internal fun hintsText(count: Int, capitalised: Boolean = true): String = when (count) {
    0 -> if (capitalised) "No hints" else "no hints"
    1 -> "1 hint"
    else -> "$count hints"
}

/** "#128 of 1,204 friends" (Friends scope) or "#128 of 1,204 players". */
internal fun HistoryDay.Standing.text(): String =
    "#$place of ${"%,d".format(Locale.US, count)} ${if (amongFriends) "friends" else "players"}"

/** The player's place on a ranked [ResultRank]. */
internal fun ResultRank.Ranked.standing() = HistoryDay.Standing(position, count, amongFriends)

/** This day with each solved game's place from [places]; games without one keep their row. */
internal fun HistoryDay.withStandings(places: Map<GameId, HistoryDay.Standing>): HistoryDay =
    copy(games = games.map { game ->
        val solved = game.outcome as? HistoryDay.Outcome.Solved
        val standing = places[game.gameId]
        if (solved == null || standing == null) game else game.copy(outcome = solved.copy(standing = standing))
    })
