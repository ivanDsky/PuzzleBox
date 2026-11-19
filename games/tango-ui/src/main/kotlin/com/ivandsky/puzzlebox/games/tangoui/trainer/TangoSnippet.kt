package com.ivandsky.puzzlebox.games.tangoui.trainer

import com.ivandsky.puzzlebox.game.trainer.TechniqueId
import com.ivandsky.puzzlebox.games.tango.TangoCell
import com.ivandsky.puzzlebox.games.tango.TangoPuzzle
import com.ivandsky.puzzlebox.games.tango.engine.trainer.TangoSnippetCut
import com.ivandsky.puzzlebox.gameui.trainer.Snippet

/**
 * One Tango snippet: [puzzle] and its [solution] (the move check judges against it) with the [cut] of its walk, the
 * grid just before the step of [technique] the player is asked to find. The player's one tap places the kind the step
 * places ([value]); the round's prompt says which.
 */
internal data class TangoSnippet(
    val puzzle: TangoPuzzle,
    val solution: List<TangoCell>,
    val cut: TangoSnippetCut,
) : Snippet {

    override val technique: TechniqueId = cut.step.rung.trainerId

    /** The kind of cell the target step fills in, the one a tap places: a sun or a moon. */
    val value: TangoCell get() = cut.step.value

    override val prompt: String
        get() = "Place a ${if (value == TangoCell.SUN) "sun" else "moon"} that ${TangoTrainerCatalog.name(technique)} proves"
}
