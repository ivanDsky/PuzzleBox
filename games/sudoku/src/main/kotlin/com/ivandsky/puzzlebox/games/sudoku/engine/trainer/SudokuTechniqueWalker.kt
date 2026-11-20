package com.ivandsky.puzzlebox.games.sudoku.engine.trainer

import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.game.trainer.TechniqueId
import com.ivandsky.puzzlebox.game.trainer.TechniqueWalker
import com.ivandsky.puzzlebox.games.sudoku.SudokuGames

/**
 * The Mini Sudoku [TechniqueWalker]: walks a puzzle the way a practice board plays it ([SudokuPractice.walk]: every open
 * cell noted, the easiest rung's deduction made at each point) and counts, per [SudokuTechniqueIds] id, the points where
 * that id was the easiest rung. So a technique is counted exactly when [SudokuPractice.start] has a board for it: every
 * puzzle the index or the drill set lists for a technique opens a practice. A stuck walk counts only the points it
 * reached; a reveal, a triple and the walk's end count for nothing.
 */
object SudokuTechniqueWalker : TechniqueWalker {

    /**
     * Version 2: the counts are the practice walk's easiest rungs, no longer the hint stepper's own choice of step
     * (which defers a single the player sees at a glance, so an id it counted could have no practice).
     */
    override val techniqueSetVersion: Int get() = 2

    /** Empty for another game's puzzle, one that does not decode, or one whose stored solution is not its unique solution. */
    override fun techniqueCounts(puzzle: Puzzle): Map<TechniqueId, Int> {
        if (puzzle.gameId != SudokuGames.MINI.id) return emptyMap()
        val decoded = (SudokuGames.MINI.decodePuzzle(puzzle.payload) as? DecodeResult.Ok)?.value ?: return emptyMap()
        if (!SudokuPractice.hasUniqueSolution(decoded)) return emptyMap()
        return SudokuPractice.walk(decoded).mapNotNull { it.rung }.groupingBy { it }.eachCount()
    }
}
