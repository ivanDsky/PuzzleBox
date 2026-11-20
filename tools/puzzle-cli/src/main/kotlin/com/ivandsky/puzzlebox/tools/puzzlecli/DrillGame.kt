package com.ivandsky.puzzlebox.tools.puzzlecli

import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.game.trainer.TechniqueId
import com.ivandsky.puzzlebox.game.trainer.TechniqueWalker
import com.ivandsky.puzzlebox.model.GameId

/**
 * One game `generate-drills` covers: its [techniques] (the ids the Trainer catalog lists), the [walker] that proves a
 * technique in a puzzle, and the [generator] of candidates.
 *
 * [families] ranks the techniques of a game whose techniques come in families, easiest lowest (Mini Sudoku: singles,
 * intersections, pairs); a drill for a technique may show nothing of a higher rank. Empty for a game with no such
 * order. [accepts] is a last check of a candidate for a target technique that the walker's counts alone cannot make,
 * for techniques the walker does not count.
 */
class DrillGame(
    val gameId: GameId,
    val techniques: List<TechniqueId>,
    val walker: TechniqueWalker,
    val generator: DrillGenerator,
    val families: Map<TechniqueId, Int> = emptyMap(),
    val accepts: (puzzle: Puzzle, target: TechniqueId) -> Boolean = { _, _ -> true },
)
