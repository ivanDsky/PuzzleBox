package com.ivandsky.puzzlebox.app

fun beside(vibrator: Vibrator) {
    vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
}
