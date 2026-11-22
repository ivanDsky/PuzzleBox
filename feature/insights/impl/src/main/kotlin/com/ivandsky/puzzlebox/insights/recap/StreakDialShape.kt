package com.ivandsky.puzzlebox.insights.recap

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.min

/**
 * The dial's size in a slot of [maxWidth] by [maxHeight]. The width is the slot's, up to [DialMaxSize], less the inset
 * either side. While the slot is no taller than wide (the tablet card) the dial is the circle it always was. Once it is
 * taller, the dial grows by the height the slot gained over its width, less [DialBreath] top and bottom, so it is continuous at
 * the square and on a phone fills the slot's height instead of floating in empty bands above and below. [DialMaxStretch] is only a
 * ceiling: the tallest real slots (a 21:9 phone's card, about 2.1 times tall) stay under it and fill; a slot beyond it
 * is not a phone, and a ring more than 2.6 times its width stops reading as a dial. A slot with no room gives a zero size.
 */
internal fun dialSize(maxWidth: Dp, maxHeight: Dp): DpSize {
    val width = max(0f, min(maxWidth.value, DialMaxSize.value) - DialInset.value * 2)
    val room = max(0f, maxHeight.value - DialInset.value * 2)
    if (maxHeight <= maxWidth) return DpSize(min(width, room).dp, min(width, room).dp)
    val height = min(width + (maxHeight.value - maxWidth.value) - DialBreath.value * 2, width * DialMaxStretch)
    return DpSize(width.dp, max(width, height).dp)
}

private val DialMaxSize = 420.dp
private val DialInset = 8.dp

// Clear space kept above and below a stretched dial, so its end ticks (and a year's ice dots, which sit outside the ring) do not touch the month label or the chips.
private val DialBreath = 16.dp
private const val DialMaxStretch = 2.6f
