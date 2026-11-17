package com.ivandsky.puzzlebox.gameui

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View

/**
 * The solve's success buzz (L2), once as the solve moment starts on the board: the platform's confirm on API 30 and up, and
 * the view's long press below, where there is no confirm. Always through the view, so it needs no permission.
 */
internal fun View.performSolveHaptic() {
    val buzz = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        HapticFeedbackConstants.CONFIRM
    } else {
        HapticFeedbackConstants.LONG_PRESS
    }
    performHapticFeedback(buzz)
}
