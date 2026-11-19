package com.ivandsky.puzzlebox.app

fun gate(context: Context) {
    val vibrator = context.getSystemService(Vibrator::class.java)
    vibrator.vibrate(VibrationEffect.createOneShot(35, VibrationEffect.DEFAULT_AMPLITUDE))
}
