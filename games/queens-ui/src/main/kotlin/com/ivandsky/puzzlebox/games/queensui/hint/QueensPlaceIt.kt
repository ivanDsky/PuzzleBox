package com.ivandsky.puzzlebox.games.queensui.hint

import com.ivandsky.puzzlebox.designsystem.spotlight.PLACE_IT
import com.ivandsky.puzzlebox.designsystem.spotlight.Spotlight
import com.ivandsky.puzzlebox.designsystem.spotlight.SpotlightAction
import com.ivandsky.puzzlebox.games.queens.engine.hints.step.QueensStep

/**
 * The "Place it" of a Queens hint callout: [placeQueen] and [crossOut] are the board's own input entries (they clear the
 * Spotlight, then commit as a tap would), so a step is applied as the player's own moves.
 *
 * A last-queen step places that queen; a cross-out step and the no-simple-step square cross out every square they name.
 * A mistake step, and a step that is not a hint at all, name nothing to place and get no action (A3). The action is
 * built when the Spotlight is, once per press, so the callout keeps one identity while it shows.
 */
internal class QueensPlaceIt(
    private val placeQueen: (Int) -> Unit,
    private val crossOut: (List<Int>) -> Unit,
) {

    /** [spotlight] with the action of [step] on its callout; the same Spotlight when [step] has none. */
    fun attach(spotlight: Spotlight, step: QueensStep): Spotlight {
        val action = actionOf(step) ?: return spotlight
        val callout = spotlight.callout ?: return spotlight
        return spotlight.copy(callout = callout.copy(action = action))
    }

    private fun actionOf(step: QueensStep): SpotlightAction? = when (step) {
        is QueensStep.Deduction -> when (val result = step.result) {
            is QueensStep.Deduction.Result.LastCell -> queen(result.cell.idx)
            // A set has no order; the sorted squares make the Undo order the same every time.
            is QueensStep.Deduction.Result.Cross -> crosses(result.cells.map { it.idx }.sorted())
        }
        is QueensStep.Reveal -> crosses(listOf(step.cell.idx))
        is QueensStep.Mistake, QueensStep.Solved, QueensStep.Unavailable -> null
    }

    private fun queen(idx: Int) = SpotlightAction(PLACE_IT) { placeQueen(idx) }

    private fun crosses(cells: List<Int>) = SpotlightAction(PLACE_IT) { crossOut(cells) }
}
