package com.ivandsky.puzzlebox.train.practice

import com.ivandsky.puzzlebox.gameui.trainer.TrainerTechnique

/** How a chapter ended for its technique: the five-in-a-row mastery it got, kept or missed. */
internal enum class ChapterOutcome { NewlyMastered, StillMastered, NotMastered }

/** How a board went, the one thing its row on the result page says: all right, an easier move missed, or Show me used (which wins over a miss). */
internal enum class BoardRowKind { Clean, Slip, Shown }

/** One board's row on the result page: its number from 1, how it went, and how many right trained moves it had. */
internal data class BoardRow(val board: Int, val kind: BoardRowKind, val moves: Int)

/**
 * What the chapter of [technique] came to, shown once its last board's hold ends (prototype 03, variant A). Every count
 * is this run through the chapter only, kept in memory; [best] is the longest right-in-a-row streak in it, uncapped.
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
    val best: Int,
    val rows: List<BoardRow>,
    val allMastered: Boolean,
    val techniques: Int,
) {
    val mastered: Boolean get() = outcome != ChapterOutcome.NotMastered
}
