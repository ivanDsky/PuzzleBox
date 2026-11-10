package com.ivandsky.puzzlebox.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.translate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke

/**
 * A ring traced around a [GameButton]'s face as it sits while disabled (sunk by the lip depth,
 * so the face fills the bounds below the lip strip), in [accent], from top centre clockwise,
 * that fills as [progress] (0..1) goes up: a cooldown the button waits out. [progress] null draws
 * nothing and adds nothing. It is read in the draw phase only, so a running fill redraws without
 * recomposing; the trace, its measure and the stroke are built once per size, and each frame
 * only refills one cached path. The stroke sits inside the bounds, so layout never moves.
 */
@Composable
fun Modifier.cooldownRing(progress: (() -> Float)?, accent: AccentSlot): Modifier {
    if (progress == null) return this
    return cooldownRing(progress, accent.colors().accent, MaterialTheme.shapes.medium)
}

private fun Modifier.cooldownRing(progress: () -> Float, color: Color, shape: Shape): Modifier = drawWithCache {
    val stroke = Stroke(width = Spacing.xs.toPx(), cap = StrokeCap.Round)
    val lip = GameButtonLipDepth.toPx()
    val face = Size(size.width, (size.height - lip).coerceAtLeast(0f))
    val measure = PathMeasure().apply { setPath(ringTrace(shape.createOutline(face, layoutDirection, this@drawWithCache), stroke.width / 2, lip), false) }
    val length = measure.length
    val filled = Path()
    onDrawWithContent {
        drawContent()
        val fraction = progress().coerceIn(0f, 1f)
        if (fraction > 0f) {
            filled.reset()
            measure.getSegment(0f, fraction * length, filled, true)
            drawPath(filled, color, style = stroke)
        }
    }
}

/** [outline] inset by [inset] on every side and moved down by [top], as one clockwise path from its top centre. */
private fun ringTrace(outline: Outline, inset: Float, top: Float): Path = when (outline) {
    is Outline.Rectangle -> roundTrace(RoundRect(outline.rect.translate(Offset(0f, top))), inset)
    is Outline.Rounded -> roundTrace(outline.roundRect.translate(Offset(0f, top)), inset)
    is Outline.Generic -> Path().apply { addPath(outline.path, Offset(0f, top)) }
}

private fun roundTrace(shape: RoundRect, inset: Float): Path {
    val left = shape.left + inset
    val top = shape.top + inset
    val right = shape.right - inset
    val bottom = shape.bottom - inset
    val tr = shape.topRightCornerRadius
    val br = shape.bottomRightCornerRadius
    val bl = shape.bottomLeftCornerRadius
    val tl = shape.topLeftCornerRadius
    fun rx(r: Float) = (r - inset).coerceAtLeast(0f)
    return Path().apply {
        moveTo((left + right) / 2, top)
        lineTo(right - rx(tr.x), top)
        arcTo(Rect(right - 2 * rx(tr.x), top, right, top + 2 * rx(tr.y)), -90f, 90f, false)
        lineTo(right, bottom - rx(br.y))
        arcTo(Rect(right - 2 * rx(br.x), bottom - 2 * rx(br.y), right, bottom), 0f, 90f, false)
        lineTo(left + rx(bl.x), bottom)
        arcTo(Rect(left, bottom - 2 * rx(bl.y), left + 2 * rx(bl.x), bottom), 90f, 90f, false)
        lineTo(left, top + rx(tl.y))
        arcTo(Rect(left, top, left + 2 * rx(tl.x), top + 2 * rx(tl.y)), 180f, 90f, false)
        lineTo((left + right) / 2, top)
    }
}

// Preview: a cooldown just started, half over and nearly done, on disabled Hint buttons.
@PuzzleBoxPreviews
@Composable
internal fun CooldownRingPreview() {
    PuzzleBoxTheme {
        Row(Modifier.background(MaterialTheme.colorScheme.surface).padding(Spacing.lg), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            listOf(0.1f, 0.5f, 0.9f).forEach { fraction ->
                GameButton(
                    onClick = {},
                    icon = PuzzleIcons.Hint,
                    label = "Hint",
                    enabled = false,
                    accent = AccentSlot.Teal,
                    modifier = Modifier.cooldownRing({ fraction }, AccentSlot.Teal),
                )
            }
        }
    }
}
