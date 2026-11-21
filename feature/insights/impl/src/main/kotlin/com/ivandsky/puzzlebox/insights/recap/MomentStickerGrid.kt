package com.ivandsky.puzzlebox.insights.recap

import com.ivandsky.puzzlebox.designsystem.RecapMotion
import kotlin.math.abs

/**
 * How the Moments sheet lays out its stickers, in dp: [columns] across, each face [size] square in a cell [cellWidth]
 * wide (the face, or its day when that is wider), the day printed under each face when [dated]. The gaps scale with the
 * face ([gapX], [gapY]). [side] and [top] are the sheet's insets: the padding, or the room a tilted, peeled sticker
 * takes past its square plus a clearance when that is more (and on top its lift), so no sticker touches the board's edge.
 */
internal data class StickerGrid(val columns: Int, val size: Float, val cellWidth: Float, val dated: Boolean) {
    val side: Float get() = maxOf(SheetPad, Reach * size + EdgeClear)
    val top: Float get() = side + RecapMotion.PeelLift.value
    val gapX: Float get() = StickerGap * size
    val gapY: Float get() = (if (dated) DayGap else StickerGap) * size
}

/**
 * The grid for [count] stickers on a sheet [widthDp] by [heightDp] (infinite on a [wide] sheet, which grows while the
 * card scrolls), each day [labelWidthDp] wide and [labelHeightDp] tall with its gap. Every column count is sized to the
 * board and capped by the phone or tablet ladder. Up to the [ReferenceMost] stickers the reference draws, its count
 * ([columnsFor]) stays unless another one makes the stickers a quarter larger or more; past that the largest stickers
 * win. The days go when they would shrink the stickers below [DatedMin]: the plaque still names the peeled sticker's
 * day, and every sticker's description keeps its own.
 */
internal fun stickerGrid(count: Int, widthDp: Float, heightDp: Float, wide: Boolean, labelWidthDp: Float, labelHeightDp: Float): StickerGrid {
    val n = count.coerceAtLeast(1)
    val dated = bestGrid(n, widthDp, heightDp, wide, labelWidthDp, labelHeightDp)
    return if (dated.size >= DatedMin) dated else bestGrid(n, widthDp, heightDp, wide, 0f, 0f).copy(dated = false)
}

/** The largest stickers [n] can have at any column count, leaning to the reference's count; days [lw] by [lh] under each. */
private fun bestGrid(n: Int, w: Float, h: Float, wide: Boolean, lw: Float, lh: Float): StickerGrid {
    val cap = if (wide) tabletSize(n) else if (n <= 2) AloneSize else MultiSize
    val prototype = columnsFor(n, wide)
    val sizes = (1..n).associateWith { c -> fitSize(n, c, w, h, cap, lw, lh) }
    val best = sizes.values.max()
    val columns = if (n <= ReferenceMost && sizes.getValue(prototype) >= ColumnPreference * best) {
        prototype
    } else {
        sizes.filterValues { it == best }.keys.minBy { abs(it - prototype) }
    }
    val size = sizes.getValue(columns)
    return StickerGrid(columns, size, maxOf(size, lw), dated = true)
}

/**
 * The largest sticker, up to [cap], for [n] stickers in [c] columns: the cells (a face, or a day [lw] wide if wider) and
 * gaps across plus both insets fit [w]; the rows of faces and days [lh] tall plus the gaps, insets and lift fit [h].
 */
private fun fitSize(n: Int, c: Int, w: Float, h: Float, cap: Float, lw: Float, lh: Float): Float {
    val rows = (n + c - 1) / c
    val rowGap = if (lh > 0f) DayGap else StickerGap
    val byFace = largest(w, 0f, c + (c - 1) * StickerGap)
    // Days wider than the faces set the cells; only the gaps and insets still grow with the face.
    val byWidth = if (byFace >= lw) byFace else largest(w, c * lw, (c - 1) * StickerGap)
    val byHeight = largest(h, rows * lh + RecapMotion.PeelLift.value, rows + (rows - 1) * rowGap)
    return minOf(cap, byWidth, byHeight).coerceAtLeast(0f)
}

/**
 * The largest face s for which [perFace]·s + [fixed] and both insets fit [room]. An inset is the larger of the padding
 * and the reach plus clearance, so s must fit with each.
 */
private fun largest(room: Float, fixed: Float, perFace: Float): Float =
    minOf(within(room - fixed - 2 * SheetPad, perFace), within(room - fixed - 2 * EdgeClear, perFace + 2 * Reach))

/** The largest s for which [perFace]·s fits [room]; any s when nothing grows with it and the room is there. */
private fun within(room: Float, perFace: Float): Float = when {
    perFace > 0f -> room / perFace
    room >= 0f -> Float.POSITIVE_INFINITY
    else -> 0f
}

/** The prototype's columns for [count] stickers: one row up to four, three for five or six, then as many as the width holds. */
private fun columnsFor(count: Int, wide: Boolean): Int = when {
    count <= 4 -> count.coerceAtLeast(1)
    count <= 6 -> 3
    count <= 8 -> 4
    wide -> 7
    else -> 5
}

/** The prototype's tablet ladder: the sticker size for [count] stickers, as large as 176dp alone down to 92dp past eight. */
private fun tabletSize(count: Int): Float = when {
    count <= 2 -> 176f
    count <= 4 -> 144f
    count <= 8 -> 128f
    else -> 92f
}

/**
 * The gaps as shares of the face: between stickers across, and between rows when the faces stand alone. Under a day the
 * day itself parts the rows, so only a smaller gap follows it.
 */
private const val StickerGap = 0.15f
private const val DayGap = 0.09f

/** The sheet's padding around the stickers, in dp. */
private const val SheetPad = 12f

/**
 * How far past its square, as a share of its size, a sticker can reach: tilted up to 9° (rest up to 4° plus the peel's
 * 5°) and scaled 1.08, a square's half-width grows to 0.618 of its size, 0.118 past the square. The curl ends inside
 * that too, as the lift raises it.
 */
private const val Reach = 0.12f

/** The least room, in dp, between the board's edge and the farthest a peeled sticker reaches. */
private const val EdgeClear = 6f

/** A phone's sticker caps: one or two alone, more beside each other. */
private const val AloneSize = 120f
private const val MultiSize = 68f

/** Below this a sticker drops its day and the stickers take the room. */
private const val DatedMin = 48f

/** Another column count wins only when the reference's count would make its stickers under 80% of that one's. */
private const val ColumnPreference = 0.8f

/** The most stickers the reference draws (its longest sheet, 5 columns on a phone and 7 on a tablet). */
private const val ReferenceMost = 14
