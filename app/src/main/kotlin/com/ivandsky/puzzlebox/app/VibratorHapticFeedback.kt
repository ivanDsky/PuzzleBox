package com.ivandsky.puzzlebox.app

import android.content.Context
import android.content.res.Resources
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.annotation.RequiresApi
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType

/**
 * Plays the kinds [effectOf] maps on the [Vibrator], as touch feedback, so the system touch-feedback switch still
 * silences them. Kinds it leaves unmapped go to [platform] unchanged.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal class VibratorHapticFeedback(
    private val vibrator: Vibrator,
    private val platform: HapticFeedback,
    private val effectOf: (HapticFeedbackType) -> VibrationEffect?,
) : HapticFeedback {
    private val touch = VibrationAttributes.createForUsage(VibrationAttributes.USAGE_TOUCH)

    override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
        val effect = effectOf(hapticFeedbackType) ?: return platform.performHapticFeedback(hapticFeedbackType)
        vibrator.vibrate(effect, touch)
    }
}

/**
 * One UI's effect per kind (#1686): plain on/off timings, lightest to heaviest. On the Tab S9 Ultra the prebaked TICK,
 * CLICK and HEAVY_CLICK played nothing, while DOUBLE_CLICK, which AOSP backs with a plain 30-100-30 ms pattern where the
 * HAL lacks it, buzzed hard: the motor plays timings, not the maker's prebaked clicks. Amplitude control makes the
 * ladder rise in strength too; without it the lengths still rise. Reject stays a double, shorter and softer than that buzz.
 */
internal fun timingEffect(type: HapticFeedbackType): VibrationEffect? = when (type) {
    HapticFeedbackType.TextHandleMove -> VibrationEffect.createOneShot(20, 100)
    HapticFeedbackType.Confirm -> VibrationEffect.createOneShot(30, 150)
    HapticFeedbackType.LongPress -> VibrationEffect.createOneShot(45, 230)
    HapticFeedbackType.Reject -> VibrationEffect.createWaveform(longArrayOf(20, 80, 20), intArrayOf(150, 0, 150), -1)
    else -> null
}

/** AOSP's light tick; its text-handle effect is hidden, TICK is the public one. */
@RequiresApi(Build.VERSION_CODES.Q)
private fun lightTickEffect(type: HapticFeedbackType): VibrationEffect? =
    if (type == HapticFeedbackType.TextHandleMove) VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK) else null

/** AOSP plays the light tick (TEXT_HANDLE_MOVE) only when the maker turns this framework bool on; stock ships it off. */
private fun Resources.systemPlaysLightTick(): Boolean {
    val id = getIdentifier("config_enableHapticTextHandle", "bool", "android")
    return id != 0 && getBoolean(id)
}

/**
 * The haptics the gate wraps. One UI drops the touch-feedback kinds the app plays (the light tick, confirm, reject:
 * #1686) before they reach the motor, and its prebaked Vibrator effects too, so there every kind plays a plain timing;
 * the support queries report the motor, not that mapping, so the maker is the signal. Elsewhere the system drops only
 * the light tick unless the maker turned it on (Pixel builds do, #1701); the framework bool says which, so only that
 * kind moves to the Vibrator there. Every other device keeps [platform] as it is.
 */
internal fun deviceHaptics(context: Context, platform: HapticFeedback): HapticFeedback {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return platform
    val effectOf: (HapticFeedbackType) -> VibrationEffect? = when {
        Build.MANUFACTURER.equals("samsung", ignoreCase = true) -> ::timingEffect
        !context.resources.systemPlaysLightTick() -> ::lightTickEffect
        else -> return platform
    }
    val vibrator = context.getSystemService(Vibrator::class.java) ?: return platform
    return VibratorHapticFeedback(vibrator, platform, effectOf)
}
