package com.ivandsky.puzzlebox.designsystem

import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType

/**
 * A [HapticFeedback] that delegates while [enabled] and drops every call while it is not. [enabled] is read at each call,
 * so a flip applies to the next buzz without a new instance.
 */
class GatedHapticFeedback(
    private val delegate: HapticFeedback,
    private val enabled: () -> Boolean,
) : HapticFeedback {
    override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
        if (enabled()) delegate.performHapticFeedback(hapticFeedbackType)
    }
}
