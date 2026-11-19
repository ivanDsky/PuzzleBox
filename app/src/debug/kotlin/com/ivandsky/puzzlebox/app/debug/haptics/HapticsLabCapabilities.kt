package com.ivandsky.puzzlebox.app.debug.haptics

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import androidx.annotation.RequiresApi

/** What the device reports about its motor and touch setting, beside the rows a tester feels (#1686). */
internal fun capabilityLines(context: Context, vibrator: Vibrator): List<String> = buildList {
    add("${Build.MANUFACTURER} ${Build.MODEL} · API ${Build.VERSION.SDK_INT}")
    val touch = Settings.System.getInt(context.contentResolver, Settings.System.HAPTIC_FEEDBACK_ENABLED, 1) != 0
    add("system touch feedback ${if (touch) "on" else "off"}")
    add("hasVibrator ${vibrator.hasVibrator()} · hasAmplitudeControl ${vibrator.hasAmplitudeControl()}")
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) add(effectSupport(vibrator))
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(primitiveSupport(vibrator))
}

@RequiresApi(Build.VERSION_CODES.R)
private fun effectSupport(vibrator: Vibrator): String {
    val answers = vibrator.areEffectsSupported(
        VibrationEffect.EFFECT_TICK,
        VibrationEffect.EFFECT_CLICK,
        VibrationEffect.EFFECT_HEAVY_CLICK,
        VibrationEffect.EFFECT_DOUBLE_CLICK,
    )
    val words = answers.map {
        when (it) {
            Vibrator.VIBRATION_EFFECT_SUPPORT_YES -> "yes"
            Vibrator.VIBRATION_EFFECT_SUPPORT_NO -> "no"
            else -> "unknown"
        }
    }
    return "areEffectsSupported " + listOf("TICK", "CLICK", "HEAVY_CLICK", "DOUBLE_CLICK").zip(words) { name, word -> "$name $word" }.joinToString(" · ")
}

@RequiresApi(Build.VERSION_CODES.S)
private fun primitiveSupport(vibrator: Vibrator): String {
    val answers = vibrator.arePrimitivesSupported(
        VibrationEffect.Composition.PRIMITIVE_TICK,
        VibrationEffect.Composition.PRIMITIVE_LOW_TICK,
        VibrationEffect.Composition.PRIMITIVE_CLICK,
        VibrationEffect.Composition.PRIMITIVE_THUD,
    )
    return "arePrimitivesSupported " + listOf("TICK", "LOW_TICK", "CLICK", "THUD").zip(answers.toList()) { name, yes -> "$name $yes" }.joinToString(" · ")
}
