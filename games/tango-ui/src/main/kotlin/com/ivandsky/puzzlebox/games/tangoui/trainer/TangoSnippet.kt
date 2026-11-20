package com.ivandsky.puzzlebox.games.tangoui.trainer

import com.ivandsky.puzzlebox.game.trainer.TechniqueId
import com.ivandsky.puzzlebox.games.tango.TangoCell
import com.ivandsky.puzzlebox.games.tango.TangoPuzzle
import com.ivandsky.puzzlebox.games.tango.engine.step.TangoStep
import com.ivandsky.puzzlebox.games.tango.engine.trainer.TangoSnippetCut
import com.ivandsky.puzzlebox.games.tango.engine.trainer.TangoTrainerMove
import com.ivandsky.puzzlebox.gameui.trainer.Snippet

/**
 * One Tango snippet: [puzzle] and its [solution] (the move check judges against it) with the [cut] of its walk, the
 * grid just before the step of [technique] the player is asked to find. The player's one tap places the kind the step
 * places ([value]); the round's prompt says which.
 *
 * [proving] carries the step that proved the player's Right move to the reveal: the board sets it before it reports the
 * verdict, and the round asks for the Spotlight ([TangoTrainer.spotlight]) right after. It belongs to this snippet, so
 * two plays never share one.
 */
internal data class TangoSnippet(
    val puzzle: TangoPuzzle,
    val solution: List<TangoCell>,
    val cut: TangoSnippetCut,
    val proving: TangoProvingStepHolder = TangoProvingStepHolder(),
) : Snippet {

    override val technique: TechniqueId = cut.step.rung.trainerId

    /** The kind of cell the target step fills in, the one a tap places: a sun or a moon. */
    val value: TangoCell get() = cut.step.value

    /** True when [move] is the one the cut's own step makes: the reveal explains that step as it is. */
    fun cutProves(move: TangoTrainerMove): Boolean = move.idx == cut.step.idx && move.value == cut.step.value

    override val prompt: String
        get() = "Tap the square where ${TangoTrainerCatalog.name(technique)} puts a ${if (value == TangoCell.SUN) "sun" else "moon"}"
}

/** The step that proved the player's Right move when it is another instance than the cut's own; null otherwise. */
internal class TangoProvingStepHolder {
    var step: TangoStep.Deduction? = null
}
