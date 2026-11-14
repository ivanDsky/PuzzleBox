package com.ivandsky.puzzlebox.gameui.board

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.util.lerp
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.rememberReducedMotion
import com.ivandsky.puzzlebox.designsystem.tabular
import kotlin.math.roundToInt

// One change of the number on its own clock: the column it rolls through and how far down it the roll is.
private class Roll(val strip: RankStrip) {
    val offset = Animatable(strip.start.toFloat())
}

// Where the number's roll is, kept for the next change: a new number that arrives mid-roll starts from the one at the
// middle, and a number that was scrubbed to is already there when the scrub hands over to the clock.
private class RollMemory {
    private var strip: RankStrip? = null
    private var offset = 0f

    /** Notes that [strip] is drawn [offset] cells down it, and returns the offset. */
    fun at(strip: RankStrip, offset: Float): Float {
        this.strip = strip
        this.offset = offset
        return offset
    }

    fun next(value: Int): Roll =
        Roll(rankStrip(strip?.centre(offset) ?: value, value)).also { at(it.strip, it.strip.start.toFloat()) }
}

/**
 * A whole number that rolls like the board's place odometer when it changes: every number between the old and the new
 * one (eight evenly spaced ones when they are far apart) scrolls past, a smaller number coming from above. [cell] draws
 * one number; the roll is as tall as the tallest cell and as wide as the cells it is between, and clips to that.
 *
 * At rest a new [value] rolls on the board's clock from whatever number is showing. Between two values the caller
 * gives the one it leaves as [from] and how far along it is as [progress] (0 to 1, read only where the roll is laid
 * out, so a frame of it recomposes nothing): the roll then follows [progress], a finger or the spring that settles it,
 * and lands without rolling again. Reduced motion draws [value] alone.
 *
 * Only the cell of [value] is left to assistive tech and tests; the ones passing by say nothing.
 */
@Composable
fun RollingNumber(
    value: Int,
    modifier: Modifier = Modifier,
    from: Int? = null,
    progress: (() -> Float)? = null,
    cell: @Composable (Int) -> Unit,
) {
    val memory = remember { RollMemory() }
    when {
        rememberReducedMotion() -> Box(modifier) { cell(value) }
        from != null && progress != null && from != value -> {
            val strip = remember(from, value) { rankStrip(from, value) }
            RollingStrip(strip, value, { memory.at(strip, strip.offsetAt(progress())) }, modifier, cell)
        }
        else -> {
            val roll = remember(value) { memory.next(value) }
            var settled by remember(roll) { mutableStateOf(!roll.strip.rolls) }
            LaunchedEffect(roll) {
                if (roll.strip.rolls) {
                    roll.offset.animateTo(roll.strip.end.toFloat(), BoardMotion.roll) { memory.at(roll.strip, this.value) }
                    settled = true
                }
            }
            if (settled) Box(modifier) { cell(value) } else RollingStrip(roll.strip, value, { roll.offset.value }, modifier, cell)
        }
    }
}

// The column of numbers, drawn [offset] cells down it. The offset is read where the column is measured and placed.
@Composable
private fun RollingStrip(strip: RankStrip, value: Int, offset: () -> Float, modifier: Modifier, cell: @Composable (Int) -> Unit) {
    Layout(
        content = {
            strip.cells.forEach { number ->
                key(number) { Box(if (number == value) Modifier else Modifier.clearAndSetSemantics { }) { cell(number) } }
            }
        },
        modifier = modifier.clipToBounds(),
    ) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val placeables = measurables.map { it.measure(loose) }
        val at = offset().coerceIn(0f, placeables.lastIndex.toFloat())
        val low = at.toInt()
        val high = (low + 1).coerceAtMost(placeables.lastIndex)
        val height = placeables.maxOf { it.height }
        layout(lerp(placeables[low].width, placeables[high].width, at - low), height) {
            placeables.forEachIndexed { index, placeable -> placeable.place(0, ((index - at) * height).roundToInt()) }
        }
    }
}

// Preview — Studio tooling only. The number at rest, and halfway from place 3 to place 12.
@PuzzleBoxPreviews
@Composable
private fun RollingNumberPreview() = PuzzleBoxTheme {
    val style = MaterialTheme.typography.displaySmall.tabular()
    Row(Modifier.padding(Spacing.md), horizontalArrangement = Arrangement.spacedBy(Spacing.lg)) {
        RollingNumber(12) { Text("#$it", style = style) }
        RollingNumber(12, from = 3, progress = { 0.5f }) { Text("#$it", style = style) }
    }
}
