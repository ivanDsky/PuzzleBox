package com.ivandsky.puzzlebox.compete.create.lineup

import com.ivandsky.puzzlebox.compete.create.DuelGame
import com.ivandsky.puzzlebox.compete.create.NewDuelState
import com.ivandsky.puzzlebox.compete.create.Opponent
import com.ivandsky.puzzlebox.data.match.LegRef
import com.ivandsky.puzzlebox.domain.lineup.ExactPuzzle
import com.ivandsky.puzzlebox.model.GameId

/**
 * What the Add / Change puzzle page shows for the row the lineup editor works on. [position] counts from 1. [solved] is
 * Past you's per-game count (null for a friend duel, and for Past you while the counts are unread, when no game is off);
 * [added] is the exact puzzles the lineup already holds.
 */
internal data class LineupPickerModel(
    val position: Int,
    val changing: Boolean,
    val games: List<DuelGame>,
    val solved: Map<GameId, Int>?,
    val added: Set<LegRef>,
    val pastYou: Boolean,
)

/** The picker for the row the lineup editor targets; null while it targets none. */
internal fun NewDuelState.lineupPicker(): LineupPickerModel? {
    val row = lineup.target ?: return null
    val pastYou = opponent == Opponent.PastYou
    return LineupPickerModel(
        position = row + 1,
        changing = lineup.changing,
        games = games,
        solved = if (pastYou) self.counts?.byGame else null,
        added = lineup.draft.entries.filterIsInstance<ExactPuzzle>().mapTo(HashSet()) { it.ref },
        pastYou = pastYou,
    )
}
