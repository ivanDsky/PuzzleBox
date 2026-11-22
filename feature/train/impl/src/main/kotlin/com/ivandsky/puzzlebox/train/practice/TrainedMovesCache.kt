package com.ivandsky.puzzlebox.train.practice

import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.game.trainer.TechniqueId
import com.ivandsky.puzzlebox.gameui.trainer.TrainerGame

/**
 * The trained-move counts a [TrainerGame] gave for puzzles, kept for as long as the practice lives so each board of a chapter
 * does not walk the same puzzles again. A count is a pure function of the puzzle and the technique. Safe to ask from
 * several threads; two asks racing for one key may both compute it.
 */
internal class TrainedMovesCache {
    private val counts = HashMap<Pair<Puzzle, TechniqueId>, Int?>()

    /** [game]'s count for [puzzle] and [technique] ([TrainerGame.trainedMoves]), computed on the first ask. */
    fun of(game: TrainerGame, puzzle: Puzzle, technique: TechniqueId): Int? {
        val key = puzzle to technique
        synchronized(counts) { if (key in counts) return counts[key] }
        val count = game.trainedMoves(puzzle, technique)
        synchronized(counts) { counts[key] = count }
        return count
    }
}
