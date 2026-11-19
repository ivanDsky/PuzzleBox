package com.ivandsky.puzzlebox.games.queensui.trainer

import com.ivandsky.puzzlebox.game.trainer.TechniqueId
import com.ivandsky.puzzlebox.gameui.trainer.Snippet
import com.ivandsky.puzzlebox.games.queens.QueensPuzzle
import com.ivandsky.puzzlebox.games.queens.engine.hints.step.QueensStep
import com.ivandsky.puzzlebox.games.queens.engine.trainer.QueensSnippetCut

/**
 * One Queens snippet: [puzzle] with the [cut] of its walk, the board just before the step of [technique] the player
 * is asked to find. The player's one tap is a queen when the step places one and a cross when it crosses squares
 * ([placesQueen]); the round's prompt says which.
 */
internal data class QueensSnippet(val puzzle: QueensPuzzle, val cut: QueensSnippetCut) : Snippet {

    override val technique: TechniqueId = requireNotNull(cut.step.unit.rung.trainerId) {
        "a snippet is cut before a step of a Trainer technique: ${cut.step}"
    }

    /** True when the target step stands a queen on its last cell; false when it crosses squares out. */
    val placesQueen: Boolean get() = cut.step.result is QueensStep.Deduction.Result.LastCell

    override val prompt: String
        get() = (if (placesQueen) "Place a queen" else "Cross a square") + " that ${QueensTrainerCatalog.name(technique)} proves"
}
