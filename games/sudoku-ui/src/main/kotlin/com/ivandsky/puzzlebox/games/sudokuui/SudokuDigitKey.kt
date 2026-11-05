package com.ivandsky.puzzlebox.games.sudokuui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

private const val TABULAR_FIGURES = "tnum"

// M3's disabled-content emphasis; the key keeps working, it only reads as done.
private const val EXHAUSTED_CONTENT_ALPHA = 0.38f

private val NOTES_BORDER = 1.dp

// Baloo 2's line boxes are much taller than its figures, so stacking two Text boxes put
// the count on the key's bottom edge in a 48dp key. The key instead places each line by
// its baseline: figures stand ~0.68em tall, and the digit + gap + count block is centred
// in the key whatever its height or the font scale.
private const val FIGURE_HEIGHT_EM = 0.68f
private val LINE_GAP = 5.sp

// The notes morph (user pick 2026-11-05, prototype H "Ink wave", sped up): each digit
// crossfades into the slot its pencil mark takes in a board cell through a 2dp blur dip,
// key by key from the notes toggle. In 200ms + 15ms a key, back in 140ms + 6ms a key.
private const val MORPH_IN_MS = 200
private const val MORPH_OUT_MS = 140
private const val STAGGER_IN_MS = 15
private const val STAGGER_OUT_MS = 6
private val MORPH_EASING = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)
private val MORPH_BLUR = 2.dp
private const val MORPH_ALPHA_DIP = 0.45f

// The pencil-mark slot grid inside the key: its inset from the key edge, the largest
// share of the slot the scaled figure may fill, and the scale it never exceeds.
private val SLOT_INSET = 5.dp
private const val SLOT_FILL = 0.92f
private const val SLOT_SCALE_MAX = 0.62f

/**
 * One digit key: the digit over its remaining count. A fully placed digit greys out
 * but stays clickable (A10), so a player can still overwrite a wrong cell with it. In notes
 * mode the key turns outlined and the digit shrinks into the slot its pencil mark takes in
 * a cell (slot `d - 1` of the box-shaped notes grid), a look no board tint shares (V7).
 * The morph runs on a graphics layer (no remeasure), [staggerOrder] keys after the one
 * nearest the notes toggle; a new toggle retargets the running one. Reduced motion snaps.
 */
@Composable
internal fun SudokuDigitKey(
    digit: Int,
    size: Int,
    remaining: Int,
    notesMode: Boolean,
    reducedMotion: Boolean,
    staggerOrder: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val exhausted = remaining == 0
    val targetContainer = when {
        exhausted -> scheme.surfaceContainerHighest
        notesMode -> scheme.surface
        else -> scheme.secondaryContainer
    }
    val targetContent = when {
        exhausted -> scheme.onSurface.copy(alpha = EXHAUSTED_CONTENT_ALPHA)
        notesMode -> scheme.onSurfaceVariant
        else -> scheme.onSecondaryContainer
    }
    // The border matches the fill outside notes mode, so only the notes key shows an outline.
    val targetBorder = if (notesMode && !exhausted) scheme.outline else targetContainer
    val delay = staggerOrder * if (notesMode) STAGGER_IN_MS else STAGGER_OUT_MS
    val crossfade: AnimationSpec<Color> = if (reducedMotion) snap() else tween(MotionTokens.DurationShort, delay)
    val container by animateColorAsState(targetContainer, crossfade, label = "digitKeyContainer")
    val content by animateColorAsState(targetContent, crossfade, label = "digitKeyContent")
    val border by animateColorAsState(targetBorder, crossfade, label = "digitKeyBorder")
    val morph = animateFloatAsState(
        targetValue = if (notesMode) 1f else 0f,
        animationSpec = when {
            reducedMotion -> snap()
            notesMode -> tween(MORPH_IN_MS, delay, MORPH_EASING)
            else -> tween(MORPH_OUT_MS, delay, MORPH_EASING)
        },
        label = "digitKeyNotesMorph",
    )
    val label = digitKeyLabel(digit, remaining, notesMode)
    val shape = MaterialTheme.shapes.medium

    Box(
        modifier = modifier
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .clip(shape)
            .background(container)
            .border(NOTES_BORDER, border, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        DigitOverCount(
            digit = digit,
            noteGrid = remember(size) { noteGrid(size) },
            digitStyle = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = TABULAR_FIGURES),
            count = remaining.toString(),
            countStyle = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = TABULAR_FIGURES),
            color = content,
            morph = { morph.value },
        )
    }
}

/**
 * The digit's and count's figures as one block centred in the key, each line placed by its
 * baseline. [morph] (0 = digit key, 1 = notes key) is read only in the layers, so the morph
 * redraws without relayout: the digit scales and moves onto its slot of [noteGrid]
 * (rows x cols), the count fades out first.
 */
@Composable
private fun DigitOverCount(
    digit: Int,
    noteGrid: Pair<Int, Int>,
    digitStyle: TextStyle,
    count: String,
    countStyle: TextStyle,
    color: Color,
    morph: () -> Float,
) {
    Layout(
        modifier = Modifier.fillMaxSize().clearAndSetSemantics {},
        content = {
            Text(text = digit.toString(), style = digitStyle, color = color, softWrap = false)
            Text(text = count, style = countStyle, color = color, softWrap = false)
        },
    ) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val digitText = measurables[0].measure(loose)
        val countText = measurables[1].measure(loose)
        val fontPx = digitStyle.fontSize.toPx()
        val digitFigure = fontPx * FIGURE_HEIGHT_EM
        val countFigure = countStyle.fontSize.toPx() * FIGURE_HEIGHT_EM
        val block = digitFigure + LINE_GAP.toPx() + countFigure
        val width = if (constraints.hasBoundedWidth) constraints.maxWidth else maxOf(digitText.width, countText.width)
        val height = if (constraints.hasBoundedHeight) constraints.maxHeight else maxOf(constraints.minHeight, 48.dp.roundToPx())
        val digitBaseline = (height - block) / 2 + digitFigure
        val countBaseline = digitBaseline + LINE_GAP.toPx() + countFigure
        val digitX = (width - digitText.width) / 2
        val digitY = (digitBaseline - digitText[FirstBaseline]).roundToInt()

        // Where the digit's figure centre goes in notes mode, and how small it gets there.
        val (rows, cols) = noteGrid
        val inset = SLOT_INSET.toPx()
        val slotWidth = (width - 2 * inset) / cols
        val slotHeight = (height - 2 * inset) / rows
        val slot = digit - 1
        val figureCentreY = digitBaseline - digitFigure / 2
        val dx = inset + (slot % cols + 0.5f) * slotWidth - width / 2f
        val dy = inset + (slot / cols + 0.5f) * slotHeight - figureCentreY
        val slotScale = minOf(SLOT_SCALE_MAX, minOf(slotWidth, slotHeight) * SLOT_FILL / digitFigure)
        val origin = TransformOrigin(0.5f, (figureCentreY - digitY) / digitText.height.coerceAtLeast(1))
        val blurPx = MORPH_BLUR.toPx()

        layout(width, height) {
            digitText.placeWithLayer(digitX, digitY) {
                val p = morph()
                val dip = 1f - abs(2f * p - 1f)
                transformOrigin = origin
                translationX = dx * p
                translationY = dy * p
                scaleX = lerp(1f, slotScale, p)
                scaleY = scaleX
                alpha = 1f - MORPH_ALPHA_DIP * dip
                renderEffect = if (dip > 0.01f) BlurEffect(blurPx * dip, blurPx * dip) else null
            }
            countText.placeWithLayer((width - countText.width) / 2, (countBaseline - countText[FirstBaseline]).roundToInt()) {
                alpha = (1f - 2f * morph()).coerceAtLeast(0f)
            }
        }
    }
}

/** The notes grid of a [size] board: boxRows x boxCols, as SudokuGeometry tiles the shipped 6x6 (2x3) and 9x9 (3x3). */
private fun noteGrid(size: Int): Pair<Int, Int> {
    val rows = (sqrt(size.toFloat()).toInt() downTo 1).first { size % it == 0 }
    return rows to size / rows
}

private fun digitKeyLabel(digit: Int, remaining: Int, notesMode: Boolean): String =
    if (notesMode) "Note $digit, $remaining left" else "$digit, $remaining left"
