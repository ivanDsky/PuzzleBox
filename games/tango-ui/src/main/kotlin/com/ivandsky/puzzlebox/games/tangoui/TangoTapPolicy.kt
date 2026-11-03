package com.ivandsky.puzzlebox.games.tangoui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.ivandsky.puzzlebox.games.tango.TangoCell
import com.ivandsky.puzzlebox.games.tango.render.TangoRendererModel

/**
 * Placement interaction (design-direction §5, A11): both variants as pure policy so
 * the decision table is unit-tested without Compose. Givens are inert in both (§5
 * shared policy). Emits [TangoPlacement]s — one for cycle/erase, first or second tap
 * of the chooser for variant B.
 */
data class TangoPlacement(val idx: Int, val value: TangoCell?)

class TangoTapPolicy(private val variant: TangoPlacementVariant) {

    /**
     * Pending chooser cell (variant B only); null when no chooser is open.
     * Compose-state-backed so the anchored chooser recomposes on change.
     */
    var pendingIdx: Int? by mutableStateOf(null)
        private set

    /**
     * Feed a tap on [idx]; returns the placements to commit (possibly empty) and
     * updates chooser state.
     */
    fun onTap(model: TangoRendererModel, idx: Int): List<TangoPlacement> {
        if (model.cells.getOrNull(idx)?.isGiven == true) {
            pendingIdx = null
            return emptyList() // givens are inert — no ripple, no chooser, no cycle
        }
        return when (variant) {
            TangoPlacementVariant.TapCycle -> {
                pendingIdx = null
                listOf(TangoPlacement(idx, next(model.cells.getOrNull(idx)?.value)))
            }
            TangoPlacementVariant.TwoTapChooser -> twoTap(model, idx)
        }
    }

    /** Chooser "Cancel" (§9 variant B dismiss). */
    fun dismissChooser() {
        pendingIdx = null
    }

    /**
     * TalkBack custom actions (§9): variant A maps "Place sun"/"Place moon"/"Clear"
     * to cycle targets — committing ONLY the final placement (review I4; the §9
     * "maps to cycle steps" copy describes the gesture model, not the commit
     * sequence). Variant B maps them to chooser + erase semantics directly. Givens
     * stay inert (empty list, never "handled").
     */
    fun onCustomAction(model: TangoRendererModel, idx: Int, label: String): List<TangoPlacement> {
        val target = when (label) {
            "Place sun" -> TangoCell.SUN
            "Place moon" -> TangoCell.MOON
            "Clear" -> null
            else -> return emptyList()
        }
        if (model.cells.getOrNull(idx)?.isGiven == true) return emptyList()
        return when (variant) {
            TangoPlacementVariant.TapCycle -> {
                // §9 describes the gesture model (repeat-tap cycle), but the commit
                // sequence must be FINAL-ONLY (review I4): TalkBack "Clear" on a sun
                // must never land the transient moon as a counted mistake. Simulate
                // the cycle locally and emit just the end state.
                val current = model.cells.getOrNull(idx)?.value
                if (current == target) {
                    emptyList() // already the target — no-op, no spurious commits
                } else {
                    listOf(TangoPlacement(idx, target))
                }
            }
            TangoPlacementVariant.TwoTapChooser -> when {
                model.cells.getOrNull(idx)?.value == target -> emptyList()
                target == null -> onTap(model, idx) // filled → clear
                else -> {
                    // Empty (or filled): chooser stamps the value directly.
                    listOf(TangoPlacement(idx, target))
                }
            }
        }
    }

    private fun twoTap(model: TangoRendererModel, idx: Int): List<TangoPlacement> {
        val pending = pendingIdx
        return when {
            // Second tap while the chooser is open: stamp the chosen value, or cancel
            // when the user taps elsewhere (outside-tap dismiss).
            pending != null -> {
                // Outside-tap dismiss (§5): the tap closes the chooser; committing the
                // tapped cell needs its own tap.
                pendingIdx = null
                emptyList()
            }
            // Tap a filled cell → clears it.
            model.cells.getOrNull(idx)?.value != null -> listOf(TangoPlacement(idx, null))
            // Tap an empty cell → open the inline chooser anchored on it (§5).
            else -> {
                pendingIdx = idx
                emptyList()
            }
        }
    }

    /** Chooser target tapped (variant B): "Place sun here" / "Place moon here". */
    fun chooserChoose(idx: Int, value: TangoCell): TangoPlacement {
        pendingIdx = null
        return TangoPlacement(idx, value)
    }

    /** Cycle step `empty → sun → moon → empty`. */
    private fun next(current: TangoCell?): TangoCell? = when (current) {
        null -> TangoCell.SUN
        TangoCell.SUN -> TangoCell.MOON
        TangoCell.MOON -> null
    }
}
