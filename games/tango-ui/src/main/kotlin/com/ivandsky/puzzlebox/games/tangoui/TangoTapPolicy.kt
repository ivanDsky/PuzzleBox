package com.ivandsky.puzzlebox.games.tangoui

import com.ivandsky.puzzlebox.games.tango.TangoCell
import com.ivandsky.puzzlebox.games.tango.render.TangoRendererModel

/**
 * Placement interaction (design-direction §5, A11): direct tap-cycle
 * `empty → sun → moon → empty` as pure policy so the decision table is unit-tested
 * without Compose. Givens are inert (§5). Emits [TangoPlacement]s to commit.
 */
data class TangoPlacement(val idx: Int, val value: TangoCell?)

object TangoTapPolicy {

    /** Feed a tap on [idx]; returns the placements to commit (possibly empty). */
    fun onTap(model: TangoRendererModel, idx: Int): List<TangoPlacement> {
        if (model.cells.getOrNull(idx)?.isGiven == true) {
            return emptyList() // givens are inert — no ripple, no cycle
        }
        return listOf(TangoPlacement(idx, next(model.cells.getOrNull(idx)?.value)))
    }

    /**
     * TalkBack custom actions (§9): "Place sun"/"Place moon"/"Clear" map to cycle
     * targets — committing ONLY the final placement (review I4; the §9 "maps to
     * cycle steps" copy describes the gesture model, not the commit sequence).
     * Givens stay inert (empty list, never "handled").
     */
    fun onCustomAction(model: TangoRendererModel, idx: Int, label: String): List<TangoPlacement> {
        val target = when (label) {
            "Place sun" -> TangoCell.SUN
            "Place moon" -> TangoCell.MOON
            "Clear" -> null
            else -> return emptyList()
        }
        if (model.cells.getOrNull(idx)?.isGiven == true) return emptyList()
        // TalkBack "Clear" on a sun must never land the transient moon as a counted
        // mistake: simulate the cycle locally and emit just the end state.
        val current = model.cells.getOrNull(idx)?.value
        return if (current == target) {
            emptyList() // already the target — no-op, no spurious commits
        } else {
            listOf(TangoPlacement(idx, target))
        }
    }

    /** Cycle step `empty → sun → moon → empty`. */
    private fun next(current: TangoCell?): TangoCell? = when (current) {
        null -> TangoCell.SUN
        TangoCell.SUN -> TangoCell.MOON
        TangoCell.MOON -> null
    }
}
