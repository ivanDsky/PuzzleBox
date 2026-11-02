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
     * to cycle steps (repeat the cycle until the target value); variant B maps them
     * to chooser + erase semantics directly.
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
                // Simulate the cycle locally (the policy is pure over the model — the
                // model still holds the old value between steps).
                val placements = mutableListOf<TangoPlacement>()
                var value: TangoCell? = model.cells.getOrNull(idx)?.value
                while (value != target && placements.size <= 3) {
                    value = when (value) {
                        null -> TangoCell.SUN
                        TangoCell.SUN -> TangoCell.MOON
                        TangoCell.MOON -> null
                    }
                    placements += TangoPlacement(idx, value)
                }
                placements
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
