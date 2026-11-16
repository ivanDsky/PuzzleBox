package com.ivandsky.puzzlebox.create.editor

import com.ivandsky.puzzlebox.game.editor.Difficulty

/**
 * The Studio strip's Finish tool (E1), as the strip sees it: [enabled] says whether a tap opens the
 * level picker, which starts on [level] (the author's last choice) and sends the pick to [onFinish];
 * [busy] says a completion is running, and a tap then goes to [onCancel]. A strip without a slot has
 * no Finish tool; the strip never learns why.
 */
data class FinishSlot(
    val enabled: Boolean,
    val busy: Boolean,
    val level: Difficulty.Level,
    val onFinish: (Difficulty.Level) -> Unit,
    val onCancel: () -> Unit,
)
