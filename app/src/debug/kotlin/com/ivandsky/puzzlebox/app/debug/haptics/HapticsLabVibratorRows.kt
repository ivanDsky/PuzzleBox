package com.ivandsky.puzzlebox.app.debug.haptics

import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.annotation.RequiresApi
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import com.ivandsky.puzzlebox.app.timingEffect

/**
 * The Vibrator rows: predefined effects with and without touch attributes, the primitives the motor supports, one-shots,
 * the AOSP double-click waveform and the app's own One UI mapping. Ids name the effect: P predefined (-T touch), C
 * primitive-scale, O ms-amplitude, W waveform timings, M mapped kind.
 */
internal fun vibratorGroups(vibrator: Vibrator): List<HapticsLabGroup> = buildList {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) add(predefinedGroup(vibrator))
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(primitiveGroup(vibrator))
    add(oneShotGroup(vibrator))
    add(
        HapticsLabGroup(
            title = "Waveform · touch · AOSP's double-click fallback, default amplitude",
            rows = listOf(HapticsLabRow("W-30-100-30") { vibrator.playAsTouch(VibrationEffect.createWaveform(longArrayOf(0, 30, 100, 30), -1)) }),
        ),
    )
    add(mappingGroup(vibrator))
}

@RequiresApi(Build.VERSION_CODES.Q)
private fun predefinedGroup(vibrator: Vibrator): HapticsLabGroup {
    val effects = listOf(
        "TICK" to VibrationEffect.EFFECT_TICK,
        "CLICK" to VibrationEffect.EFFECT_CLICK,
        "HEAVY" to VibrationEffect.EFFECT_HEAVY_CLICK,
        "DOUBLE" to VibrationEffect.EFFECT_DOUBLE_CLICK,
    )
    return HapticsLabGroup(
        title = "Predefined · -T touch usage, bare no attributes",
        rows = effects.flatMap { (name, effect) ->
            listOf(
                HapticsLabRow("P-$name-T") { vibrator.playAsTouch(VibrationEffect.createPredefined(effect)) },
                HapticsLabRow("P-$name") { vibrator.vibrate(VibrationEffect.createPredefined(effect)) },
            )
        },
    )
}

@RequiresApi(Build.VERSION_CODES.S)
private fun primitiveGroup(vibrator: Vibrator): HapticsLabGroup {
    val supported = listOf(
        "TICK" to VibrationEffect.Composition.PRIMITIVE_TICK,
        "LOWTICK" to VibrationEffect.Composition.PRIMITIVE_LOW_TICK,
        "CLICK" to VibrationEffect.Composition.PRIMITIVE_CLICK,
        "THUD" to VibrationEffect.Composition.PRIMITIVE_THUD,
    ).filter { (_, primitive) -> vibrator.areAllPrimitivesSupported(primitive) }
    return HapticsLabGroup(
        title = if (supported.isEmpty()) "Primitives · none supported" else "Primitives · touch, supported only",
        rows = supported.flatMap { (name, primitive) ->
            listOf(0.3f, 0.6f, 1.0f).map { scale ->
                HapticsLabRow("C-$name-$scale") {
                    vibrator.playAsTouch(VibrationEffect.startComposition().addPrimitive(primitive, scale).compose())
                }
            }
        },
    )
}

private fun oneShotGroup(vibrator: Vibrator) = HapticsLabGroup(
    title = "One-shot · touch · ms-amplitude",
    rows = listOf(10L, 20L, 30L, 50L).flatMap { ms ->
        listOf(60, 120, 255).map { amplitude ->
            HapticsLabRow("O-$ms-$amplitude") { vibrator.playAsTouch(VibrationEffect.createOneShot(ms, amplitude)) }
        }
    },
)

private fun mappingGroup(vibrator: Vibrator): HapticsLabGroup {
    val kinds = listOf(
        "TICK" to HapticFeedbackType.TextHandleMove,
        "CONFIRM" to HapticFeedbackType.Confirm,
        "REJECT" to HapticFeedbackType.Reject,
        "LONG" to HapticFeedbackType.LongPress,
    )
    return HapticsLabGroup(
        title = "The app's One UI mapping · touch",
        rows = kinds.map { (name, kind) -> HapticsLabRow("M-$name") { timingEffect(kind)?.let(vibrator::playAsTouch) } },
    )
}

private fun Vibrator.playAsTouch(effect: VibrationEffect) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_TOUCH))
    } else {
        vibrate(effect)
    }
}
