package com.ivandsky.puzzlebox.train.practice

import com.ivandsky.puzzlebox.gameui.trainer.TrainerTechnique

/** How a chapter ended for its technique: the five-right-without-a-miss mastery it got, kept or missed. */
internal enum class ChapterOutcome { NewlyMastered, StillMastered, NotMastered }

/**
 * How a board went, the one thing its row on the result page says: all right, a move made out of order, moves
 * undone, or Show me used. Show me wins over undone moves, which win over a move out of order.
 */
internal enum class BoardRowKind { Clean, Slip, Undone, Shown }

/** One board's row on the result page: its number from 1, how it went, how many right trained moves it had, and how many moves were undone as wrong. */
internal data class BoardRow(val board: Int, val kind: BoardRowKind, val moves: Int, val undone: Int = 0)

/**
 * What the chapter of [technique] came to, shown once its last board's hold ends (prototype 03, variant A). Every count
 * is this run through the chapter only, kept in memory; [best] is the longest streak of right moves in it, uncapped.
 * [slips] counts moves made out of order (a correct move the easiest technique could not make yet) and [undone] moves
 * undone as wrong.
 * [missedRun] is true when a wrong move or a Show me on a trained move broke the streak and the run never reached the
 * mastery count: after the last miss the chapter had too few moves left, so only a new run can master it.
 * [next] is the technique after it in the game's catalog, null for the last. [allMastered] is true when every one of
 * the catalog's [techniques] is mastered, which only the last technique's note says.
 */
internal data class ChapterResult(
    val technique: TrainerTechnique,
    val next: TrainerTechnique?,
    val outcome: ChapterOutcome,
    val boards: Int,
    val boardsDone: Int,
    val rights: Int,
    val shown: Int,
    val slips: Int,
    val undone: Int,
    val best: Int,
    val missedRun: Boolean = false,
    val rows: List<BoardRow>,
    val allMastered: Boolean,
    val techniques: Int,
) {
    val mastered: Boolean get() = outcome != ChapterOutcome.NotMastered
}
