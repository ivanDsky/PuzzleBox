package com.ivandsky.puzzlebox.games.sudokuui.trainer

import com.ivandsky.puzzlebox.game.trainer.TechniqueId
import com.ivandsky.puzzlebox.games.sudoku.SudokuPuzzle
import com.ivandsky.puzzlebox.games.sudoku.engine.hints.step.SudokuStep
import com.ivandsky.puzzlebox.games.sudoku.engine.trainer.SudokuSnippetCut
import com.ivandsky.puzzlebox.games.sudoku.engine.trainer.SudokuTechniqueIds
import com.ivandsky.puzzlebox.gameui.trainer.Snippet

/**
 * One Mini Sudoku snippet: [puzzle] (the move check judges against its solution) with the [cut] of its walk, the board
 * just before the step of [technique] the player is asked to find. A snippet for a single asks for a digit, one for an
 * elimination asks for a note to strike ([placesDigit] tells which); the round's prompt says so.
 *
 * [digits] carries the board's digit look to the reveal: the round asks for the Spotlight ([SudokuTrainer.spotlight])
 * before the board has measured anything, so the board publishes its look here as it composes. It belongs to this
 * snippet, so two rounds never share one.
 */
internal data class SudokuSnippet(
    val puzzle: SudokuPuzzle,
    val cut: SudokuSnippetCut,
    val digits: SudokuDigitStyleHolder = SudokuDigitStyleHolder(),
) : Snippet {

    override val technique: TechniqueId =
        checkNotNull(SudokuTechniqueIds.of(cut.step)) { "a cut holds a step of no listed technique: ${cut.step}" }

    /** True when the target step places a digit (a last cell or a single), false when it strikes notes. */
    val placesDigit: Boolean get() = cut.step is SudokuStep.Single

    override val prompt: String
        get() = SudokuTrainerCatalog.name(technique).let { name ->
            if (placesDigit) "Tap the square $name fills, then pick its digit" else "Tap a square, then pick a note $name rules out"
        }
}
