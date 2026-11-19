package com.ivandsky.puzzlebox.sample

// vibrator.vibrate(effect) and VibratorManager in a comment
fun good(vibrator: Vibrator, haptics: HapticFeedback) {
    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
    val effect = VibrationEffect.createOneShot(30, 255)
    val canBuzz = vibrator.hasVibrator()
    val s = "getSystemService(Vibrator::class.java) VibratorManager vibrate("
}
