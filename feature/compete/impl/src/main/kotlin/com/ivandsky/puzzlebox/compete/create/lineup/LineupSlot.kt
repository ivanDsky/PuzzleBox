package com.ivandsky.puzzlebox.compete.create.lineup

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.zIndex
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.designsystem.rememberReducedMotion

private const val LIFT_SCALE = 1.02f

/** A drag in progress: the [reorder], the draft it began on, and whether the finger has [dropped] the row. */
internal data class ActiveDrag(val reorder: LineupReorder, val base: LineupDraft, val dropped: Boolean = false)

/**
 * Where one row of the lineup sits while [drag] runs: the lifted row follows the finger, raised and a little larger, the
 * others glide aside to make room, and once the drop is [landed] (the rows are in their new order) the dropped row
 * glides from where the finger left it into its slot and [onSettled] says the drag is over. [content] gets the
 * modifier to place the row with, and whether it is lifted.
 */
@Composable
internal fun LineupSlot(
    index: Int,
    drag: ActiveDrag?,
    landed: Boolean,
    onSize: (Int) -> Unit,
    onSettled: () -> Unit,
    content: @Composable (Modifier, Boolean) -> Unit,
) {
    val reduced = rememberReducedMotion()
    val spec: AnimationSpec<Float> = if (reduced) snap() else tween(MotionTokens.DurationMedium, easing = MotionTokens.EasingStandard)
    val reorder = drag?.reorder
    val lifted = reorder != null && !landed && reorder.lifted == index
    val settling = reorder != null && landed && reorder.target == index

    val shift = remember { Animatable(0f) }
    val shiftTarget = if (reorder == null || landed || lifted) 0f else reorder.shiftOf(index)
    LaunchedEffect(shiftTarget, landed) {
        if (landed) shift.snapTo(0f) else shift.animateTo(shiftTarget, spec)
    }
    // Created in the composition the drop lands in, so the row never shows a frame at its slot before it starts from the finger.
    val settle = remember(settling) { Animatable(if (settling && reorder != null) reorder.settleFrom else 0f) }
    LaunchedEffect(settling) {
        if (settling) {
            settle.animateTo(0f, spec)
            onSettled()
        }
    }
    val scale by animateFloatAsState(if (lifted) LIFT_SCALE else 1f, spec, label = "lineup row lift")

    val placed = Modifier
        .zIndex(if (lifted || settling) 1f else 0f)
        .onSizeChanged { onSize(it.height) }
        .graphicsLayer {
            translationY = when {
                lifted && reorder != null -> reorder.offset
                settling -> settle.value
                landed -> 0f
                else -> shift.value
            }
            scaleX = scale
            scaleY = scale
        }
    content(placed, lifted)
}
