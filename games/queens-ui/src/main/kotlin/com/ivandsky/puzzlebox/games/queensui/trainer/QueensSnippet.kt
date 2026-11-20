package com.ivandsky.puzzlebox.games.queensui.trainer

import com.ivandsky.puzzlebox.game.trainer.TechniqueId
import com.ivandsky.puzzlebox.gameui.trainer.Snippet
import com.ivandsky.puzzlebox.games.queens.QueensPuzzle
import com.ivandsky.puzzlebox.games.queens.engine.hints.step.QueensStep
import com.ivandsky.puzzlebox.games.queens.engine.trainer.QueensSnippetCut
import com.ivandsky.puzzlebox.games.queens.engine.trainer.QueensTrainerMove

/**
 * One Queens snippet: [puzzle] with the [cut] of its walk, the board just before the step of [technique] the player
 * is asked to find. The player's one tap is a queen when the step places one and a cross when it crosses squares
 * ([placesQueen]); the round's prompt says which.
 *
 * [proving] carries the step that proved the player's Right move to the reveal: the board sets it before it reports the
 * verdict, and the round asks for the Spotlight ([QueensTrainer.spotlight]) right after. It belongs to this snippet, so
 * two plays never share one.
 */
internal data class QueensSnippet(
    val puzzle: QueensPuzzle,
    val cut: QueensSnippetCut,
    val proving: QueensProvingStepHolder = QueensProvingStepHolder(),
) : Snippet {

    override val technique: TechniqueId = requireNotNull(cut.step.unit.rung.trainerId) {
        "a snippet is cut before a step of a Trainer technique: ${cut.step}"
    }

    /** True when the target step stands a queen on its last cell; false when it crosses squares out. */
    val placesQueen: Boolean get() = cut.step.result is QueensStep.Deduction.Result.LastCell

    /** True when [move] is one the cut's own step makes: the reveal explains that step as it is. */
    fun cutProves(move: QueensTrainerMove): Boolean = when (val result = cut.step.result) {
        is QueensStep.Deduction.Result.LastCell -> move is QueensTrainerMove.Place && move.cell == result.cell
        is QueensStep.Deduction.Result.Cross -> move is QueensTrainerMove.Cross && move.cell in result.cells
    }

    override val prompt: String
        get() = QueensTrainerCatalog.name(technique).let { name ->
            if (placesQueen) "Tap the square where $name puts a queen" else "Tap a square $name rules out"
        }
}

/** The step that proved the player's Right move when it is another instance than the cut's own; null otherwise. */
internal class QueensProvingStepHolder {
    var step: QueensStep.Deduction? = null
}
