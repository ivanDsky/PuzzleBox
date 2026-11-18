package com.ivandsky.puzzlebox.games.tangoui.hint

import com.ivandsky.puzzlebox.designsystem.spotlight.PLACE_IT
import com.ivandsky.puzzlebox.designsystem.spotlight.Spotlight
import com.ivandsky.puzzlebox.designsystem.spotlight.SpotlightAction
import com.ivandsky.puzzlebox.games.tango.TangoCell
import com.ivandsky.puzzlebox.games.tango.engine.step.TangoStep

/**
 * The "Place it" action a Tango hint's callout carries (PlaceIt): one tap puts the explained mark on the board.
 * A deduction and a reveal explain a cell and its value, so they get the action; a mistake names a wrong entry, not
 * a move to make (A3), so it never does. [place] is the controller's own placement entry, so the mark is the player's
 * own move. The returned Spotlight is [spotlight] itself when the step has no action.
 */
internal fun Spotlight.withPlaceIt(step: TangoStep, place: (idx: Int, value: TangoCell) -> Unit): Spotlight {
    val (idx, value) = when (step) {
        is TangoStep.Deduction -> step.idx to step.value
        is TangoStep.Reveal -> step.idx to step.value
        is TangoStep.Mistake, TangoStep.Solved, TangoStep.Unavailable -> return this
    }
    val bubble = callout ?: return this
    return copy(callout = bubble.copy(action = SpotlightAction(PLACE_IT) { place(idx, value) }))
}
