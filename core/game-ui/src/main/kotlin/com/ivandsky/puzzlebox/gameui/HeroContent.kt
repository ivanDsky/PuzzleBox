package com.ivandsky.puzzlebox.gameui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.offset

/**
 * The hero's content, its children in order: the header, the time, then the bottom group (the best line when there
 * is one, the chips and the actions when there are any) stacked inside [padding]. The time keeps [timeGapTop] under
 * the header and [timeGapBottom] above what follows; the other children carry their own spacing.
 *
 * Given more height than that (the card's minimum, from [modifier]) the content falls into three zones: the header
 * stays on the top padding, the bottom group rests on the bottom padding, and the time is centred in the space
 * between them. It grows to use that space by a scale of at most [MaxTimeScale], which also leaves it the larger of
 * its gaps clear above and below and keeps it inside the width. The scale is applied at placement, as a layer from
 * the time's start edge, so it never changes a measured size: the time is measured at its own size, and the natural
 * height passed to [onNaturalHeight] (with the padding) is the same however tall the card is stretched. The screen can
 * therefore set the minimum from it without a loop. The report is made here because the stretched node's own size is
 * no longer the natural one for `onSizeChanged` to see; writing the same height again is a no-op for the screen's
 * state, so only a real change relays out the cards. With no spare height the blocks sit as in a plain column.
 */
@Composable
internal fun HeroContent(
    padding: Dp,
    timeGapTop: Dp,
    timeGapBottom: Dp,
    modifier: Modifier,
    onNaturalHeight: ((Int) -> Unit)?,
    content: @Composable () -> Unit,
) {
    Layout(content = content, modifier = modifier) { measurables, constraints ->
        val pad = padding.roundToPx()
        val gapTop = timeGapTop.roundToPx()
        val gapBottom = timeGapBottom.roundToPx()
        val free = constraints.offset(horizontal = -2 * pad, vertical = -2 * pad).copy(minHeight = 0)
        val blocks = measurables.map { it.measure(free) }
        val header = blocks[0]
        val time = blocks[1]
        val group = blocks.drop(2)
        val groupHeight = group.sumOf { it.height }
        val natural = header.height + gapTop + time.height + gapBottom + groupHeight + 2 * pad
        onNaturalHeight?.invoke(natural)
        val width = constraints.constrainWidth(blocks.maxOf { it.width } + 2 * pad)
        val height = constraints.constrainHeight(natural)
        layout(width, height) {
            header.place(pad, pad)
            val headerBottom = pad + header.height
            val groupTop = height - pad - groupHeight
            var y = groupTop
            group.forEach { block ->
                block.place(pad, y)
                y += block.height
            }
            if (height == natural) {
                time.place(pad, headerBottom + gapTop)
            } else {
                val clearance = maxOf(gapTop, gapBottom)
                val fitHeight = (groupTop - headerBottom - 2 * clearance).toFloat() / time.height
                val fitWidth = (width - 2 * pad).toFloat() / time.width
                val scale = minOf(MaxTimeScale, fitHeight, fitWidth).coerceAtLeast(1f)
                val centred = (headerBottom + groupTop - time.height) / 2
                val top = centred.coerceIn(headerBottom + gapTop, groupTop - gapBottom - time.height)
                time.placeWithLayer(pad, top) {
                    scaleX = scale
                    scaleY = scale
                    transformOrigin = TransformOrigin(0f, 0.5f)
                }
            }
        }
    }
}

/** How far the stretched hero card may grow its time past its own size; the space and the width cap it first. */
private const val MaxTimeScale = 1.6f
