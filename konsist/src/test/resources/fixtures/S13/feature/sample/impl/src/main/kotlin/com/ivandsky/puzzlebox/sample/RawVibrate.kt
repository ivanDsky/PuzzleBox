package com.ivandsky.puzzlebox.sample

fun raw(vibrator: Vibrator) {
    vibrator.vibrate(VibrationEffect.createOneShot(30, 255), attributes)
}
