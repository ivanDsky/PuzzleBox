package com.ivandsky.puzzlebox.play.friend

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.model.ShareCode
import kotlinx.coroutines.delay

/** Whether this showing of a friend's card [animates] its sections in, or starts settled. */
@Immutable
internal class CardEntrance(val animates: Boolean)

/**
 * The entrance for [code]'s card: it animates the first time the card is shown, and never again
 * for that code while its saved state lives, so recomposing, coming back from the board or a
 * restored instance state show the card settled.
 */
@Composable
internal fun rememberCardEntrance(code: ShareCode): CardEntrance {
    var played by rememberSaveable(code.value) { mutableStateOf(false) }
    val entrance = remember(code.value) { CardEntrance(animates = !played) }
    LaunchedEffect(code.value) { played = true }
    return entrance
}

/**
 * One section of the card's [entrance]: rises [MotionTokens.EnterRise] and fades in after [index]
 * staggers; under [reduced] motion it only fades. A settled entrance shows it at rest.
 */
@Composable
internal fun Enter(entrance: CardEntrance, index: Int, reduced: Boolean, content: @Composable () -> Unit) {
    val shown = remember(entrance) { Animatable(if (entrance.animates) 0f else 1f) }
    LaunchedEffect(shown) {
        if (shown.value == 1f) return@LaunchedEffect
        if (reduced) {
            shown.animateTo(1f, tween(MotionTokens.ReducedFadeDuration))
        } else {
            delay(index.coerceAtMost(MotionTokens.EnterStaggerCap) * MotionTokens.EnterStagger.toLong())
            shown.animateTo(1f, tween(MotionTokens.EnterDuration, easing = MotionTokens.EasingEmphasizedDecelerate))
        }
    }
    val rise = with(LocalDensity.current) { MotionTokens.EnterRise.toPx() }
    Box(
        Modifier.graphicsLayer {
            alpha = shown.value
            translationY = if (reduced) 0f else (1f - shown.value) * rise
        },
    ) { content() }
}
