package com.ivandsky.puzzlebox.create.editor

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The verdict card's holder (02 handoff, "Pill popover"): whether the card is [open], and
 * [checkAgain], which hands the open draft back to the verdict pipeline through [recheck].
 * The pill opens it; an outside tap, Back or any board edit closes it.
 */
class VerdictCardState(private val recheck: () -> Unit) {

    private val mutableOpen = MutableStateFlow(false)
    val open: StateFlow<Boolean> = mutableOpen.asStateFlow()

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
}
