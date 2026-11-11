package com.ivandsky.puzzlebox.designsystem

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.dp
import com.airbnb.android.showkase.annotation.ShowkaseComposable
import kotlin.math.hypot

/**
 * A [SolveSweepState] tied to this composition. The sweep lasts
 * [MotionTokens.SweepDuration]. Reduced motion (and the preview inspector, which
 * never runs effects past the first frame) jumps straight to the end: the accent
 * outline with no band.
 */
@Composable
fun rememberSolveSweepState(): SolveSweepState {
    val state = remember { SolveSweepState() }
    val instant = rememberReducedMotion() || LocalInspectionMode.current
    LaunchedEffect(state, state.plays) {
        if (state.plays == 0) return@LaunchedEffect
        if (instant) {
            state.clock.snapTo(1f)
        } else {
            state.clock.snapTo(0f)
            state.clock.animateTo(1f, tween(MotionTokens.SweepDuration, easing = LinearEasing))
        }
    }
    return state
}

/**
 * The solve moment over a board: once [state] plays, a diagonal band of [color] at
 * MotionTokens.SweepAlpha crosses the board top-left to bottom-right, clipped to [shape],
 * while the board outline turns [color] at MotionTokens.SweepOutlineWidth. The outline
 * stays after the band has gone. Draw-phase only: playing never recomposes the board.
 */
fun Modifier.solveSweep(state: SolveSweepState, color: Color, shape: Shape): Modifier =
    this then SolveSweepElement(state, color, shape)

private data class SolveSweepElement(
    val state: SolveSweepState,
    val color: Color,
    val shape: Shape,
) : ModifierNodeElement<SolveSweepNode>() {
    override fun create() = SolveSweepNode(state, color, shape)

    override fun update(node: SolveSweepNode) {
        node.state = state
        node.color = color
        node.shape = shape
        node.invalidateDraw()
    }
}

private class SolveSweepNode(
    var state: SolveSweepState,
    var color: Color,
    var shape: Shape,
) : Modifier.Node(), DrawModifierNode {
    override fun ContentDrawScope.draw() {
        drawContent()
        if (state.plays == 0) return
        drawSweep(state.clock.value)
    }

    private fun ContentDrawScope.drawSweep(t: Float) {
        val board = shape.createOutline(size, layoutDirection, this)

        val band = MotionTokens.EasingStandard.transform(t)
        if (band < 1f) {
            // The gradient axis runs corner to corner; the band's width is a fraction of the
            // board width, so its share of the axis is that width over the diagonal.
            val half = MotionTokens.SweepBandFraction * size.width / hypot(size.width, size.height) / 2f
            val center = -half + band * (1f + 2f * half)
            val fill = color.copy(alpha = MotionTokens.SweepAlpha)
            val from = (center - half).coerceIn(0f, 1f)
            val to = (center + half).coerceIn(0f, 1f)
            val brush = Brush.linearGradient(
                0f to Color.Transparent,
                from to Color.Transparent,
                from to fill,
                to to fill,
                to to Color.Transparent,
                1f to Color.Transparent,
                start = Offset.Zero,
                end = Offset(size.width, size.height),
            )
            clipPath(Path().apply { addOutline(board) }) { drawRect(brush) }
        }

        val outlineProgress = (t * MotionTokens.SweepDuration / MotionTokens.SweepOutlineDuration).coerceAtMost(1f)
        val stroke = MotionTokens.SweepOutlineWidth.toPx()
        // Inset by half the stroke so the outline stays inside the board bounds.
        val inset = shape.createOutline(Size(size.width - stroke, size.height - stroke), layoutDirection, this)
        translate(stroke / 2f, stroke / 2f) {
            drawOutline(
                outline = inset,
                color = color.copy(alpha = MotionTokens.EasingStandard.transform(outlineProgress)),
                style = Stroke(width = stroke),
            )
        }
    }
}

// Previews — Studio tooling only (internal for Showkase's generated index). The sweep's
// end frame: the band has crossed, the accent outline stays. A8: the pane is static —
// the sweep plays once on entry and rests there.
@ShowkaseComposable(name = "SolveSweep", group = "Motion")
@PuzzleBoxPreviews
@Composable
internal fun SolveSweepPreview() {
    PuzzleBoxTheme {
        val sweep = rememberSolveSweepState()
        LaunchedEffect(sweep) { sweep.play() }
        val shape = MaterialTheme.shapes.large
        Box(Modifier.background(MaterialTheme.colorScheme.background).padding(Spacing.md)) {
            Box(
                Modifier
                    .size(160.dp)
                    .background(MaterialTheme.colorScheme.surface, shape)
                    .solveSweep(sweep, AccentSlot.Blue.colors().accent, shape),
            )
        }
    }
}
