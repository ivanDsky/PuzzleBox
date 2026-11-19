package com.ivandsky.puzzlebox.app

import android.content.Context
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.annotation.RequiresApi
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType

/**
 * Plays each app haptic kind as the predefined [Vibrator] effect AOSP maps it to, as touch feedback, so the system
 * touch-feedback switch still silences it. Kinds with no mapping go to [platform] unchanged.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal class VibratorHapticFeedback(
    private val vibrator: Vibrator,
    private val platform: HapticFeedback,
) : HapticFeedback {
    private val touch = VibrationAttributes.createForUsage(VibrationAttributes.USAGE_TOUCH)

    override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
        val effect = touchEffect(hapticFeedbackType) ?: return platform.performHapticFeedback(hapticFeedbackType)
        vibrator.vibrate(effect, touch)
    }
}

/** AOSP's effect per kind (PhoneWindowManager / HapticFeedbackVibrationProvider); its text-handle tick is hidden, TICK is the public one. */
@RequiresApi(Build.VERSION_CODES.Q)
internal fun touchEffect(type: HapticFeedbackType): VibrationEffect? = when (type) {
    HapticFeedbackType.TextHandleMove -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
    HapticFeedbackType.Confirm -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
    HapticFeedbackType.Reject -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_DOUBLE_CLICK)
    HapticFeedbackType.LongPress -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK)
    else -> null
}

/**
 * The haptics the gate wraps. One UI drops the touch-feedback kinds the app plays (the light tick, confirm, reject:
 * #1686) before they reach the motor, while its Vibrator plays predefined effects; the support queries report the
 * motor, not that mapping, so the maker is the signal. Every other device keeps [platform] as it is.
 */
internal fun deviceHaptics(context: Context, platform: HapticFeedback): HapticFeedback {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || !Build.MANUFACTURER.equals("samsung", ignoreCase = true)) return platform
    val vibrator = context.getSystemService(Vibrator::class.java) ?: return platform
    return VibratorHapticFeedback(vibrator, platform)
}
