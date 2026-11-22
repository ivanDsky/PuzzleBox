package com.ivandsky.puzzlebox.insights.recap

import kotlin.math.hypot

/**
 * One sticker on a month card's stamp board, in dp from the board's top-left: its centre ([cx], [cy]), its [size] and
 * its rest [tilt] in degrees. [reach] is the circle it may take up, rim and shadow included; [caption] is the date's box
 * under it.
 */
internal data class PlacedStamp(val cx: Float, val cy: Float, val size: Float, val tilt: Float) {
    val reach: Float get() = size / 2f * ReachScale + ReachPad
    val caption: StampCaption
        get() {
            val top = cy + size / 2f + CaptionGap
            return StampCaption(cx - CaptionWidth / 2f, top, cx + CaptionWidth / 2f, top + CaptionHeight)
        }
}

/** The box a sticker's date takes under it, in dp. */
internal data class StampCaption(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    fun overlaps(other: StampCaption): Boolean =
        left < other.right && other.left < right && top < other.bottom && other.top < bottom

    /** How far the point ([x], [y]) is from this box; 0 inside it. */
    fun distanceTo(x: Float, y: Float): Float {
        val dx = maxOf(left - x, 0f, x - right)
        val dy = maxOf(top - y, 0f, y - bottom)
        return hypot(dx, dy)
    }
}

/** A board's sticker unit in dp: 30% of its width or 36% of its height, whichever is less, kept to 72–150dp. */
internal fun boardUnit(widthDp: Float, heightDp: Float): Float = minOf(WidthShare * widthDp, 0.36f * heightDp).coerceIn(MinUnit, MaxUnit)

/**
 * Where [count] (0 to [MaxPlaced]) stickers sit on a board [widthDp] by [heightDp]: the prototype's preset for the count,
 * each sticker the [boardUnit] times its preset scale, then all shrunk together by the largest factor up to 1 at which
 * no sticker, date or board edge runs into another. A board too short for the preset's two rows (a phone that gives it
 * little height) gets the same stickers in one zigzag row instead, when the plain row would keep them about a sixth
 * larger or more; that row is sized by the board's width and fitted with the zigzag as tall as the board allows, so a
 * short phone board still gets stickers close to the prototype's.
 */
internal fun stampPlacement(count: Int, widthDp: Float, heightDp: Float): List<PlacedStamp> {
    val preset = Presets.getOrNull(count - 1) ?: return emptyList()
    val unit = boardUnit(widthDp, heightDp)
    val scattered = preset.fitted(unit, widthDp, heightDp)
    val plainRow = preset.inOneRow().fitted(unit, widthDp, heightDp)
    if (scattered.minOf { it.size } >= RowPreference * plainRow.minOf { it.size }) return scattered
    val zigzag = preset.zigzagRow(minOf(WidthShare * widthDp, MaxUnit), widthDp, heightDp)
    return if (zigzag != null && zigzag.minOf { it.size } > plainRow.minOf { it.size }) zigzag else plainRow
}

/** These stickers placed at [unit], shrunk by the largest factor up to 1 that [fits] the board. */
private fun List<Preset>.fitted(unit: Float, width: Float, height: Float): List<PlacedStamp> {
    val at = { k: Float -> map { (x, y, tilt, scale) -> PlacedStamp(x / 100f * width, y / 100f * height, unit * scale * k, tilt) } }
    val full = at(1f)
    if (full.fits(width, height)) return full
    var lo = 0f
    var hi = 1f
    repeat(FitSteps) {
        val mid = (lo + hi) / 2f
        if (at(mid).fits(width, height)) lo = mid else hi = mid
    }
    return at(lo)
}

/**
 * These stickers spread across one row from edge to edge, every other one pinned to the top edge and the rest to the
 * bottom edge (their dates inside it), at [unit] times their scale shrunk by the largest factor up to 1 that [fits] the
 * board with [ZigzagGap] between neighbours; null when no size does.
 */
private fun List<Preset>.zigzagRow(unit: Float, width: Float, height: Float): List<PlacedStamp>? {
    val at = { k: Float ->
        val first = PlacedStamp(0f, 0f, unit * first().scale * k, 0f).reach
        val last = PlacedStamp(0f, 0f, unit * last().scale * k, 0f).reach
        mapIndexed { i, p ->
            val size = unit * p.scale * k
            val x = if (this.size == 1) width / 2f else first + i * (width - first - last) / (this.size - 1)
            val cy = if (i % 2 == 0) {
                size / 2f * ReachScale + ReachPad + ZigzagMargin
            } else {
                height - size / 2f - CaptionGap - CaptionHeight - ZigzagMargin
            }
            PlacedStamp(x, cy, size, p.tilt)
        }
    }
    // Pinned to the edges, a smaller sticker does not always fit where a larger one does, so step down from full size
    // rather than bisect.
    return (ZigzagSteps downTo 1).map { at(it / ZigzagSteps.toFloat()) }.firstOrNull { it.fits(width, height, ZigzagGap) }
}

/** The same stickers, tilts and scales spread evenly across one row, every other one a little lower. */
private fun List<Preset>.inOneRow(): List<Preset> = mapIndexed { i, p ->
    Preset((i + 0.5f) / size * 100f, RowY + if (i % 2 == 0) -RowStagger else RowStagger, p.tilt, p.scale)
}

/** The row sits at [RowY]% down, every other sticker [RowStagger]% higher or lower; it wins below [RowPreference]. */
private const val RowY = 42f
private const val RowStagger = 5f
private const val RowPreference = 0.85f

/** True when every sticker and date is inside the board and clear of every other one by at least [gap]. */
private fun List<PlacedStamp>.fits(width: Float, height: Float, gap: Float = 0f): Boolean = withIndex().all { (i, a) ->
    val c = a.caption
    a.cx - a.reach >= 0f && a.cx + a.reach <= width && a.cy - a.reach >= 0f && a.cy + a.reach <= height &&
        c.left >= 0f && c.right <= width && c.bottom <= height &&
        withIndex().all { (j, b) ->
            j == i || (
                c.distanceTo(b.cx, b.cy) >= b.reach + gap &&
                    (j < i || (hypot(a.cx - b.cx, a.cy - b.cy) >= a.reach + b.reach + gap && !c.overlaps(b.caption)))
                )
        }
}

/** The most stickers a board places; past this it shows [MaxPlaced] - 1 and "+N more". */
internal const val MaxPlaced = 6

private data class Preset(val x: Float, val y: Float, val tilt: Float, val scale: Float)

/** The prototype's presets per count: x% and y% of the board, rest tilt in degrees, scale of the unit. */
private val Presets = listOf(
    listOf(Preset(50f, 46f, -6f, 1.3f)),
    listOf(Preset(30f, 34f, -8f, 1.1f), Preset(70f, 56f, 7f, 1.1f)),
    listOf(Preset(26f, 30f, -9f, 1f), Preset(72f, 34f, 6f, 1f), Preset(48f, 62f, -3f, 1.05f)),
    listOf(Preset(27f, 26f, -8f, 1f), Preset(72f, 24f, 7f, 1f), Preset(30f, 64f, 5f, 1f), Preset(72f, 66f, -6f, 1f)),
    listOf(Preset(20f, 25f, -8f, 0.92f), Preset(52f, 20f, 5f, 0.92f), Preset(81f, 30f, -5f, 0.92f), Preset(34f, 64f, 6f, 0.98f), Preset(68f, 66f, -7f, 0.98f)),
    listOf(
        Preset(20f, 24f, -8f, 0.86f), Preset(50f, 19f, 6f, 0.86f), Preset(80f, 26f, -5f, 0.86f),
        Preset(24f, 64f, 6f, 0.92f), Preset(52f, 68f, -6f, 0.92f), Preset(80f, 65f, 5f, 0.92f),
    ),
)

/** The share of a board's width a sticker unit takes. */
private const val WidthShare = 0.30f
private const val MinUnit = 72f
private const val MaxUnit = 150f
private const val ReachScale = 1.05f
private const val ReachPad = 3f
private const val FitSteps = 20

/** The zigzag row's sizes tried, from full in 1% steps; it is stepped, not bisected, as pinned stickers fit non-monotonically. */
private const val ZigzagSteps = 100

/** The room a zigzag sticker keeps from the board's edge, so it is never tangent to it. */
private const val ZigzagMargin = 0.5f

/**
 * The room the zigzag row keeps between a sticker's reach and its neighbours', dp. The reach is a circle; a tilted
 * rounded square's corners poke out past it, so neighbours are kept this far apart to leave a visible gap.
 */
private const val ZigzagGap = 10f

/** A sticker's date box: [CaptionWidth] by [CaptionHeight] dp, [CaptionGap] under the sticker. */
internal const val CaptionWidth = 44f
internal const val CaptionHeight = 16f
internal const val CaptionGap = 8f
