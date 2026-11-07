package com.ivandsky.puzzlebox.games.queensui.editor

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.designsystem.PillShape
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.colors
import com.ivandsky.puzzlebox.games.queens.editor.QueensDraft
import com.ivandsky.puzzlebox.games.queensui.LocalQueensReducedMotion
import com.ivandsky.puzzlebox.games.queensui.QueensBrand
import com.ivandsky.puzzlebox.games.queensui.QueensRegionPalette
import com.ivandsky.puzzlebox.games.queensui.drawTextureDots
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The select-then-colour ring (prototype 03 b): [colours] 48 dp circles evenly spaced from
 * 12 o'clock on a surface disc, a dashed outline on colours missing from [used], a "next"
 * tag on the first of those, an accent ring on [current] (the colour the whole selection
 * already has), and a centre ✕ that picks `QueensDraft.UNPAINTED`. Every pick goes to [onPick].
 */
@Composable
internal fun QueensColourRing(
    colours: Int,
    used: Set<Int>,
    current: Int?,
    onPick: (region: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val radiusPx = with(LocalDensity.current) { QueensEditorDefaults.ringRadius(colours).toPx() }
    val next = (0 until colours).firstOrNull { it !in used }
    Box(
        modifier = modifier
            .size(QueensEditorDefaults.ringDiameter(colours))
            .testTag("queens.editor.ring")
            .shadow(QueensEditorDefaults.ringDiscElevation, CircleShape)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = QueensEditorDefaults.ringDiscAlpha), CircleShape)
            .swallowTouches(),
        contentAlignment = Alignment.Center,
    ) {
        for (region in 0 until colours) {
            val angle = 2 * PI * region / colours
            RingPetal(
                region = region,
                unused = region !in used,
                isNext = region == next,
                isCurrent = region == current,
                onPick = onPick,
                modifier = Modifier.offset {
                    IntOffset((radiusPx * sin(angle)).roundToInt(), (-radiusPx * cos(angle)).roundToInt())
                },
            )
        }
        UnpaintCentre(onPick)
    }
}

@Composable
private fun RingPetal(
    region: Int,
    unused: Boolean,
    isNext: Boolean,
    isCurrent: Boolean,
    onPick: (Int) -> Unit,
    modifier: Modifier,
) {
    val fill = QueensRegionPalette.fill(region, isSystemInDarkTheme())
    val ink = QueensRegionPalette.onInkFor(fill, MaterialTheme.colorScheme.onSurface, MaterialTheme.colorScheme.scrim)
    val outline = MaterialTheme.colorScheme.outline
    val accent = QueensBrand.accent.colors()
    val letter = ('A' + region).toString()
    val interaction = remember { MutableInteractionSource() }
    Box(modifier, contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(QueensEditorDefaults.ringPetalSize)
                .pressFeedback(interaction)
                .drawBehind { if (isCurrent) drawCurrentRing(accent.accent) }
                .clip(CircleShape)
                .background(fill)
                .drawWithContent {
                    if (QueensRegionPalette.textured(region)) drawPetalTexture(ink)
                    drawContent()
                    if (unused) drawDashedOutline(outline)
                }
                .testTag("queens.editor.ring.slot.$region")
                .clickable(interaction, indication = null, role = Role.Button) { onPick(region) }
                .semantics { contentDescription = if (unused) "Colour $letter (unused)" else "Colour $letter" },
            contentAlignment = Alignment.Center,
        ) {
            Text(letter, color = ink, style = MaterialTheme.typography.titleSmall, modifier = Modifier.clearAndSetSemantics {})
        }
        if (isNext) {
            Text(
                text = "next",
                color = accent.onAccent,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .offset(y = QueensEditorDefaults.ringNextDrop)
                    .background(accent.accent, PillShape)
                    .padding(horizontal = Spacing.xs)
                    .clearAndSetSemantics {},
            )
        }
    }
}

@Composable
private fun UnpaintCentre(onPick: (Int) -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        Modifier
            .size(QueensEditorDefaults.ringPetalSize)
            .pressFeedback(interaction)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .border(QueensEditorDefaults.ringCentreStroke, MaterialTheme.colorScheme.outline, CircleShape)
            .testTag("queens.editor.ring.unpaint")
            .clickable(interaction, indication = null, role = Role.Button) { onPick(QueensDraft.UNPAINTED) },
        contentAlignment = Alignment.Center,
    ) {
        Icon(PuzzleIcons.Close, contentDescription = "Unpaint", tint = MaterialTheme.colorScheme.onSurface)
    }
}

/** Press / Tap feedback: 0.97 over 120 ms; none under reduced motion. */
@Composable
private fun Modifier.pressFeedback(interaction: MutableInteractionSource): Modifier {
    val pressed by interaction.collectIsPressedAsState()
    val still = LocalQueensReducedMotion.current
    val scale by animateFloatAsState(
        targetValue = if (pressed && !still) MotionTokens.PressScale else 1f,
        animationSpec = tween(MotionTokens.DurationShort),
        label = "ring-press",
    )
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/** The disc takes every touch the circles miss, so a tap on it never reaches the board. */
private fun Modifier.swallowTouches(): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        do {
            val event = awaitPointerEvent()
            event.changes.forEach { it.consume() }
        } while (event.changes.any { it.pressed })
    }
}

private fun DrawScope.drawCurrentRing(accent: Color) {
    val stroke = QueensEditorDefaults.ringCurrentStroke.toPx()
    drawCircle(accent, radius = size.minDimension / 2 + stroke / 2, style = Stroke(stroke))
}

private fun DrawScope.drawDashedOutline(outline: Color) {
    val stroke = QueensEditorDefaults.ringUnusedStroke.toPx()
    val dash = QueensEditorDefaults.ringUnusedDash.toPx()
    drawCircle(
        outline,
        radius = size.minDimension / 2 - stroke / 2,
        style = Stroke(stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, dash))),
    )
}

/** The board's K/L dot texture (ticket 20), so a repeated hue still reads apart on the ring. */
private fun DrawScope.drawPetalTexture(ink: Color) {
    val radius = maxOf(QueensRegionPalette.textureDotMinRadius.toPx(), size.minDimension * QueensRegionPalette.textureDotRadiusFraction)
    drawTextureDots(ink.copy(alpha = QueensRegionPalette.textureDotAlpha), radius, 0f, 0f, size.width, size.height)
}

@PuzzleBoxPreviews
@Preview(name = "Empty 9×9: only A used, B is next", group = "Queens ring")
@Composable
private fun QueensColourRingEmptyPreview() {
    PuzzleBoxTheme { QueensColourRing(colours = 9, used = setOf(0), current = 0, onPick = {}) }
}

@PuzzleBoxPreviews
@Preview(name = "12×12: all used, K/L textured", group = "Queens ring")
@Composable
private fun QueensColourRingDensePreview() {
    PuzzleBoxTheme { QueensColourRing(colours = 12, used = (0 until 12).toSet(), current = null, onPick = {}) }
}
