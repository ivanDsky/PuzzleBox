package com.ivandsky.puzzlebox.play

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.semantics
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.designsystem.rememberReducedMotion
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * How much of a [MorphCell] layer shows, 0 to 1. Opacity is invisible to semantics otherwise, and a host test has no
 * pixels to read it from.
 */
internal val MorphAlpha = SemanticsPropertyKey<Float>("MorphAlpha")

/**
 * Draws [content] for [value] and, when [value] changes, morphs to the new one in place: the old layer fades and blurs
 * out while the new one fades and un-blurs in, stacked in the same spot ([MotionTokens.DayMorphOut],
 * [MotionTokens.DayMorphIn], [MotionTokens.DayMorphDelay], [MotionTokens.DayMorphBlur]). Nothing translates or clips,
 * so a glyph is never cut. The cell is as large as its largest layer. A value that is not new keeps its layer and
 * does not move: that is what lets a card keep its unchanged text outside the cells that change.
 *
 * [identity] says when two values are the same layer: values that share it update the layer in place instead of
 * morphing. A change that lands mid-morph turns the layer then arriving into one leaving, from its current opacity,
 * and starts the next. Reduced motion swaps at once.
 */
@Composable
internal fun <T> MorphCell(
    value: T,
    modifier: Modifier = Modifier,
    identity: (T) -> Any? = { it },
    content: @Composable (T) -> Unit,
) {
    val reducedMotion = rememberReducedMotion()
    val state = remember { MorphLayers(value) }
    LaunchedEffect(value, reducedMotion) { state.show(value, identity, reducedMotion) }
    Box(modifier, contentAlignment = Alignment.CenterStart) {
        if (reducedMotion) {
            // Drawn straight from the value, not from the layers the effect below catches up, so the swap does not
            // wait a frame.
            content(value)
            return@Box
        }
        state.layers.forEach { layer ->
            key(layer.id) {
                val alpha = layer.alpha.value
                Box(
                    Modifier
                        .semantics { this[MorphAlpha] = alpha }
                        .blur(MotionTokens.DayMorphBlur * (1f - alpha), BlurredEdgeTreatment.Unbounded)
                        .graphicsLayer { this.alpha = layer.alpha.value },
                    contentAlignment = Alignment.CenterStart,
                ) { content(layer.value) }
            }
        }
    }
}

private class MorphLayer<T>(val id: Int, value: T, alpha: Float) {
    var value by mutableStateOf(value)
    val alpha = Animatable(alpha)
}

private class MorphLayers<T>(first: T) {
    private var nextId = 1
    val layers: SnapshotStateList<MorphLayer<T>> = mutableStateListOf(MorphLayer(0, first, 1f))

    suspend fun show(value: T, identity: (T) -> Any?, reducedMotion: Boolean) {
        val newest = layers.last()
        if (reducedMotion) {
            newest.value = value
            layers.removeAll { it !== newest }
            newest.alpha.snapTo(1f)
            return
        }
        if (identity(newest.value) == identity(value)) {
            newest.value = value
            newest.alpha.animateTo(1f, Arriving)
            return
        }
        val leaving = layers.toList()
        val arriving = MorphLayer(nextId++, value, 0f)
        layers.add(arriving)
        coroutineScope {
            leaving.forEach { layer ->
                launch {
                    layer.alpha.animateTo(0f, tween(MotionTokens.DayMorphOut, easing = MotionTokens.EasingEase))
                    layers.remove(layer)
                }
            }
            launch { arriving.alpha.animateTo(1f, Arriving) }
        }
    }
}

private val Arriving = tween<Float>(MotionTokens.DayMorphIn, MotionTokens.DayMorphDelay, MotionTokens.EasingEase)
