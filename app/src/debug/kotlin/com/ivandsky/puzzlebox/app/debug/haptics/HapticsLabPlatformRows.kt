package com.ivandsky.puzzlebox.app.debug.haptics

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View

/**
 * The platform rows: the View constants the system maps to its own patterns, the keyboard's tap among them, played on
 * [view]. S9 lets only this file reach the View directly (Ruling, #1686): comparing them is what the lab is for.
 */
internal fun platformGroup(view: View) = HapticsLabGroup(
    title = "View constants · system touch setting applies",
    rows = platformConstants().map { (name, constant) -> HapticsLabRow("V-$name") { view.performHapticFeedback(constant) } },
)

private fun platformConstants(): List<Pair<String, Int>> = buildList {
    add("CLOCK_TICK" to HapticFeedbackConstants.CLOCK_TICK)
    add("KEYBOARD_TAP" to HapticFeedbackConstants.KEYBOARD_TAP)
    add("VIRTUAL_KEY" to HapticFeedbackConstants.VIRTUAL_KEY)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        add("CONFIRM" to HapticFeedbackConstants.CONFIRM)
        add("REJECT" to HapticFeedbackConstants.REJECT)
    }
    add("LONG_PRESS" to HapticFeedbackConstants.LONG_PRESS)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) add("TEXT_HANDLE_MOVE" to HapticFeedbackConstants.TEXT_HANDLE_MOVE)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) add("GESTURE_END" to HapticFeedbackConstants.GESTURE_END)
}
