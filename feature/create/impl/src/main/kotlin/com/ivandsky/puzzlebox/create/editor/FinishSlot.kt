package com.ivandsky.puzzlebox.create.editor

/**
 * The Studio strip's Finish tool (E1), as the strip sees it: [enabled] says whether a press starts a
 * completion, [busy] that one is running (a press then cancels it) and [onPress] is the press. A strip
 * without a slot has no Finish tool; the strip never learns why.
 */
data class FinishSlot(val enabled: Boolean, val busy: Boolean, val onPress: () -> Unit)
