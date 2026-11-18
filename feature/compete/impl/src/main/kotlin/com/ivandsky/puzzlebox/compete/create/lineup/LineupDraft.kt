package com.ivandsky.puzzlebox.compete.create.lineup

import com.ivandsky.puzzlebox.compete.create.DuelGame
import com.ivandsky.puzzlebox.data.match.LegRef
import com.ivandsky.puzzlebox.data.match.Match
import com.ivandsky.puzzlebox.domain.lineup.AnyGame
import com.ivandsky.puzzlebox.domain.lineup.ExactPuzzle
import com.ivandsky.puzzlebox.domain.lineup.LineupEntry
import com.ivandsky.puzzlebox.domain.lineup.PuzzleSource
import com.ivandsky.puzzlebox.model.GameId

/** The ordered puzzles of a duel being built: one to [Match.MAX_LEGS]. Pure; every edit returns a new draft, and one that can't apply returns this. */
internal data class LineupDraft(val entries: List<LineupEntry>) {
    val size: Int get() = entries.size
    val canAdd: Boolean get() = size < Match.MAX_LEGS

    /** The last puzzle stays: a duel is never empty. */
    val canRemove: Boolean get() = size > MIN_ENTRIES

    fun added(entry: LineupEntry): LineupDraft = if (canAdd) copy(entries = entries + entry) else this

    fun replaced(index: Int, entry: LineupEntry): LineupDraft =
        if (index in entries.indices) copy(entries = entries.toMutableList().also { it[index] = entry }) else this

    fun removed(index: Int): LineupDraft =
        if (canRemove && index in entries.indices) copy(entries = entries.filterIndexed { at, _ -> at != index }) else this

    /** The puzzle at [from] taken out and put back so it ends at [to]; the others keep their order. */
    fun moved(from: Int, to: Int): LineupDraft {
        if (from == to || from !in entries.indices || to !in entries.indices) return this
        return copy(entries = entries.toMutableList().apply { add(to, removeAt(from)) })
    }

    /** Every row is the puzzle [available] says is today's for its game: the rows of the Daily duel, which neither Add nor a change may touch. */
    fun isToday(available: List<DuelGame>): Boolean = entries.isNotEmpty() && entries.all { entry ->
        val ref = (entry as? ExactPuzzle)?.ref as? LegRef.Daily ?: return@all false
        available.any { it.id == ref.gameId && it.todayDay == ref.day && it.todayNumber == ref.number }
    }

    companion object {
        const val MIN_ENTRIES = 1
        const val OPENING_ENTRIES = 3

        /** Today's puzzle of each of [available] the player hasn't solved, one row each in order; empty when none is numbered. */
        fun today(available: List<DuelGame>): LineupDraft = LineupDraft(
            available.filterNot { it.solvedToday }.mapNotNull { game ->
                val day = game.todayDay ?: return@mapNotNull null
                val number = game.todayNumber ?: return@mapNotNull null
                ExactPuzzle(LegRef.Daily(game.id, day, number), authorUid = null, title = "${game.name} #$number", author = null, source = PuzzleSource.PastDay)
            }.take(Match.MAX_LEGS),
        )

        /** What Quick draws, as rows: three "Any", one per game in turn; empty until [available] is known. */
        fun opening(available: List<DuelGame>): LineupDraft =
            LineupDraft(if (available.isEmpty()) emptyList() else List(OPENING_ENTRIES) { AnyGame(available[it % available.size].id) })

        /**
         * What Quick draws for Past you, as rows: "Any" of each game in turn, a game dropping out once its [solved] puzzles
         * are all in rows, so no game has more rows than it has solved; empty when nothing is solved.
         */
        fun openingSolved(available: List<DuelGame>, solved: Map<GameId, Int>): LineupDraft {
            fun DuelGame.solvedBeyond(round: Int) = (solved[id] ?: 0) > round
            val rounds = generateSequence(0) { it + 1 }.takeWhile { round -> available.any { it.solvedBeyond(round) } }
            return LineupDraft(rounds.flatMap { round -> available.filter { it.solvedBeyond(round) } }.take(OPENING_ENTRIES).map { AnyGame(it.id) }.toList())
        }
    }
}
