package com.ivandsky.puzzlebox.create.editor

import com.ivandsky.puzzlebox.game.editor.Verdict
import com.ivandsky.puzzlebox.gameui.editor.EditorOverlay
import com.ivandsky.puzzlebox.gameui.editor.GameEditorController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The verdict card's holder (02 handoff, "Pill popover"): whether the card is [open], and
 * [checkAgain], which hands the open draft back to the verdict pipeline through [recheck].
 * The pill opens it; an outside tap, Back or any board edit closes it.
 *
 * On a 2+ solutions verdict it drives the A/B witness choice, which the open [controller]
 * owns as its `overlay`: each new settled Multiple [read] resets it to that controller's
 * `openingOverlay`, on the board as well while [autoShow] (the author's saved Auto-show) is on,
 * and every [choose] goes to its `adoptOverlay`; the card reads the controller's
 * overlay itself, so it and the board read one value. Checking keeps the choice, as the board holds its last settled reading.
 */
class VerdictCardState(
    private val recheck: () -> Unit,
    private val controller: () -> GameEditorController? = { null },
    private val autoShow: () -> Boolean,
) {

    private val mutableOpen = MutableStateFlow(false)
    val open: StateFlow<Boolean> = mutableOpen.asStateFlow()

    private var lastMultiple: Verdict.Multiple? = null

    fun show() {
        mutableOpen.value = true
    }

    fun dismiss() {
        mutableOpen.value = false
    }

    /** Runs the analysis again on the same draft; the card stays open to show the new verdict. */
    fun checkAgain() {
        recheck()
    }

    /** Each verdict the Studio shows: a new settled Multiple opens the witness choice afresh, on the board while [autoShow]. */
    fun read(verdict: Verdict) {
        if (verdict !is Verdict.Multiple || verdict == lastMultiple) return
        lastMultiple = verdict
        val controller = controller() ?: return
        val opening = controller.openingOverlay(verdict)
        controller.adoptOverlay(opening.copy(showOnBoard = opening.showOnBoard || autoShow()))
    }

    /** The author's A/B or Show on board change; the board draws it at once. */
    fun choose(overlay: EditorOverlay) {
        controller()?.adoptOverlay(overlay)
    }
}
