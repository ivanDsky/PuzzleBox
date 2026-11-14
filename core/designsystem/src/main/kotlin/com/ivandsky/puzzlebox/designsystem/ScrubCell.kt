package com.ivandsky.puzzlebox.designsystem

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

// The morph's whole clock: the arriving layer starts DayMorphDelay late and takes DayMorphIn.
private val MorphMillis = (MotionTokens.DayMorphDelay + MotionTokens.DayMorphIn).toFloat()

/** How much of the leaving layer shows when the morph is [progress] of the way: gone after [MotionTokens.DayMorphOut] of its clock. */
internal fun morphLeaving(progress: Float): Float = 1f - (progress * MorphMillis / MotionTokens.DayMorphOut).coerceIn(0f, 1f)

/** How much of the arriving layer shows when the morph is [progress] of the way: it starts [MotionTokens.DayMorphDelay] late. */
internal fun morphArriving(progress: Float): Float =
    ((progress * MorphMillis - MotionTokens.DayMorphDelay) / MotionTokens.DayMorphIn).coerceIn(0f, 1f)

/**
 * [MorphCell]'s morph with its clock in the caller's hands: [content] of [from] frosts out and [content] of [to]
 * frosts in, stacked in the same spot at [alignment], as [progress] runs 0 to 1 (a drag, or the spring that settles
 * it). The two share the morph's proportions: the old one is gone early, the new one starts a moment late. Equal
 * values are one layer that does nothing. [progress] is read only when the layers are drawn, so scrubbing it
 * recomposes nothing; the cell is as large as the larger layer, and nothing translates or clips.
 */
@Composable
fun <T> ScrubCell(
    from: T,
    to: T,
    progress: () -> Float,
    modifier: Modifier = Modifier,
    alignment: Alignment = Alignment.CenterStart,
    content: @Composable (T) -> Unit,
) {
    Box(modifier, contentAlignment = alignment) {
        if (from == to) {
            content(to)
        } else {
            Box(Modifier.frosted { morphLeaving(progress()) }, contentAlignment = alignment) { content(from) }
            Box(Modifier.frosted { morphArriving(progress()) }, contentAlignment = alignment) { content(to) }
        }
    }
}

// Preview — Studio tooling only. The cell a third of the way from one board's title to the next.
@PuzzleBoxPreviews
@Composable
internal fun ScrubCellPreview() {
    PuzzleBoxTheme {
        ScrubCell("Zip · today", "Tango · today", { 1f / 3 }, Modifier.padding(Spacing.md)) {
            Text(it, style = MaterialTheme.typography.titleMedium)
        }
    }
}
