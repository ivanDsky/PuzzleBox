package com.ivandsky.puzzlebox.history.ranks

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.designsystem.rememberReducedMotion
import kotlin.math.abs
import kotlin.math.sign
import kotlinx.coroutines.launch

/** A drag this far across the board (a quarter of its width) commits the swipe. */
internal const val SWIPE_COMMIT_FRACTION = 0.25f

/** The board follows the finger this much of the way: a hint of the swipe, not a page slide. */
private const val FOLLOW = 0.2f

private val FLING_VELOCITY = 700.dp

/**
 * How many boards a horizontal drag of [dragPx] released at [velocityPx] (both px, negative = leftwards)
 * walks: one forward for a leftward swipe, one back for a rightward one, none when it is neither a
 * fling ([flingPx]/s) nor [SWIPE_COMMIT_FRACTION] of [widthPx]. A fling decides by its own direction.
 */
internal fun swipeStep(dragPx: Float, velocityPx: Float, widthPx: Float, flingPx: Float): Int = when {
    abs(velocityPx) >= flingPx -> -sign(velocityPx).toInt()
    abs(dragPx) >= widthPx * SWIPE_COMMIT_FRACTION -> -sign(dragPx).toInt()
    else -> 0
}

/**
 * Horizontal swipe on [content] that reports one step per committed swipe through [onStep] (+1 next,
 * -1 previous). It claims only a horizontal drag, so a vertical scroll that starts on the board goes to
 * the scroll parent. The content leans a little with the finger and settles back; the new board
 * is the parent's re-ranking of the same list, not a slide. Assistive tech gets both steps as actions.
 */
@Composable
internal fun BoardSwipe(onStep: (Int) -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val scope = rememberCoroutineScope()
    val lean = remember { Animatable(0f) }
    var width by remember { mutableFloatStateOf(0f) }
    var travelled by remember { mutableFloatStateOf(0f) }
    val fling = with(LocalDensity.current) { FLING_VELOCITY.toPx() }
    val still = rememberReducedMotion()
    val state = rememberDraggableState { delta ->
        travelled += delta
        if (!still) scope.launch { lean.snapTo(travelled * FOLLOW) }
    }
    Box(
        modifier
            .onSizeChanged { width = it.width.toFloat() }
            .semantics {
                customActions = listOf(
                    CustomAccessibilityAction("Next board") { onStep(1); true },
                    CustomAccessibilityAction("Previous board") { onStep(-1); true },
                )
            }
            .draggable(
                state,
                Orientation.Horizontal,
                onDragStarted = { travelled = 0f },
                onDragStopped = { velocity ->
                    val step = swipeStep(travelled, velocity, width, fling)
                    travelled = 0f
                    if (step != 0) onStep(step)
                    lean.animateTo(0f, spring(MotionTokens.SpringDampingDefault, Spring.StiffnessMediumLow))
                },
            )
            .graphicsLayer { translationX = lean.value },
    ) { content() }
}
