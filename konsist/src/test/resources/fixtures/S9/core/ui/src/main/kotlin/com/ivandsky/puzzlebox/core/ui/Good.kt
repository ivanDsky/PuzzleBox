package com.ivandsky.puzzlebox.core.ui

// view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
val s = "HapticFeedbackConstants"
fun ok(haptics: HapticFeedback) = haptics.performHapticFeedback(HapticFeedbackType.Confirm)
fun myHapticFeedbackConstants() = Unit
