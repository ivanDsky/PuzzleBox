package com.ivandsky.puzzlebox.games.sudokuui.trainer

import com.ivandsky.puzzlebox.game.trainer.TechniqueId
import com.ivandsky.puzzlebox.games.sudoku.SudokuPuzzle
import com.ivandsky.puzzlebox.games.sudoku.engine.hints.step.SudokuStep
import com.ivandsky.puzzlebox.games.sudoku.engine.trainer.SudokuSnippetCut
import com.ivandsky.puzzlebox.games.sudoku.engine.trainer.SudokuTechniqueIds
import com.ivandsky.puzzlebox.games.sudoku.engine.trainer.SudokuTrainerMove
import com.ivandsky.puzzlebox.gameui.trainer.Snippet

/**
 * One Mini Sudoku snippet: [puzzle] (the move check judges against its solution) with the [cut] of its walk, the board
 * just before the step of [technique] the player is asked to find. A snippet for a single asks for a digit, one for an
 * elimination asks for a note to strike ([placesDigit] tells which); the round's prompt says so.
 *
 * [digits] carries the board's digit look to the reveal: the round asks for the Spotlight ([SudokuTrainer.spotlight])
 * before the board has measured anything, so the board publishes its look here as it composes. It belongs to this
 * snippet, so two rounds never share one.
 *
 * [proving] carries the step that proved the player's Right move to the reveal: the board sets it before it reports the
 * verdict, and the round asks for the Spotlight right after. It belongs to this snippet too.
 */
internal data class SudokuSnippet(
    val puzzle: SudokuPuzzle,
    val cut: SudokuSnippetCut,
    val digits: SudokuDigitStyleHolder = SudokuDigitStyleHolder(),
    val proving: SudokuProvingStepHolder = SudokuProvingStepHolder(),
) : Snippet {

    override val technique: TechniqueId =
        checkNotNull(SudokuTechniqueIds.of(cut.step)) { "a cut holds a step of no listed technique: ${cut.step}" }

    /** True when the target step places a digit (a last cell or a single), false when it strikes notes. */
    val placesDigit: Boolean get() = cut.step is SudokuStep.Single

    /** True when [move] is one the cut's own step makes: the reveal explains that step as it is. */
    fun cutProves(move: SudokuTrainerMove): Boolean = when (val step = cut.step) {
        is SudokuStep.Single -> move is SudokuTrainerMove.Place && move.idx == step.place.cell && move.digit == step.place.digit
        is SudokuStep.Elimination ->
            move is SudokuTrainerMove.RemoveNote && (step.struck[move.idx] ?: 0) and (1 shl (move.digit - 1)) != 0
        else -> false
    }

    override val prompt: String
        get() = SudokuTrainerCatalog.name(technique).let { name ->
            if (placesDigit) "Tap the square $name fills, then pick its digit" else "Tap a square, then pick a note $name rules out"
        }
}

/** The step that proved the player's Right move when it is another instance than the cut's own; null otherwise. */
internal class SudokuProvingStepHolder {
    var step: SudokuStep? = null
}
