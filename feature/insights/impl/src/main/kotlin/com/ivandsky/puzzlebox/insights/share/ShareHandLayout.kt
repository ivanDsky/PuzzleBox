package com.ivandsky.puzzlebox.insights.share

import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.designsystem.RecapMotion
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * One tile of the hand: [game], [width] by [height] px inside its rings, at rest [tilt] degrees about its bottom centre
 * and [drop] px down; [fromX] px from the middle of the row, where the stack it fans out of stands.
 */
internal data class HandSlot(
    val game: RecapShareContent.GameCount,
    val width: Int,
    val height: Int,
    val tilt: Float,
    val drop: Float,
    val fromX: Float,
)

/**
 * The hand of [games] at rest, left to right (handoff section 4). Ranked by solves (ties keep the games' order), the
 * first in the middle and each next one outward, alternately after and before it. A tile is
 * `TileMinWidth + TileWidthRange × (solved / most)^0.7` wide, all of them scaled down together when the row would not
 * fit; tilted `TileTilt` degrees and dropped `TileDrop × d²` px for its distance d from the middle.
 */
internal fun handSlots(games: List<RecapShareContent.GameCount>): List<HandSlot> {
    if (games.isEmpty()) return emptyList()
    val ranked = games.sortedByDescending { it.solved }
    val most = ranked.first().solved.toFloat()
    val row = ArrayDeque<RecapShareContent.GameCount>()
    ranked.forEachIndexed { i, game -> if (i % 2 == 0) row.addFirst(game) else row.addLast(game) }
    val natural = row.map { ShareStripe.TileMinWidth + ShareStripe.TileWidthRange * (it.solved / most).pow(ShareStripe.WidthCurve) }
    val k = row.size
    val room = ShareStripe.RowWidth - ShareStripe.TileGap * (k - 1) - ShareStripe.RowSlack
    val scale = min(1f, room / natural.sum())
    val widths = natural.map { (it * scale).roundToInt() }
    val boxes = widths.map { it + ShareStripe.Ring * 4 }
    val spacing = ShareStripe.TileGap - ShareStripe.Ring * 4
    val total = boxes.sum() + spacing * (k - 1)
    val middle = (k - 1) / 2f
    var left = 0
    return row.mapIndexed { i, game ->
        val d = i - middle
        val fromX = total / 2f - (left + boxes[i] / 2f)
        left += boxes[i] + spacing
        HandSlot(game, widths[i], (widths[i] * ShareStripe.TileAspect).roundToInt(), d * ShareStripe.TileTilt, ShareStripe.TileDrop * d * d, fromX)
    }
}

/** How far tile [index] is through its fan-out at [ms] since the hand started: 0 in the stack, 1 in its slot (past 1 while it overshoots). */
internal fun fanProgress(ms: Float, index: Int): Float =
    RecapMotion.EasingSpring.transform(((ms - index * RecapMotion.FanStagger) / RecapMotion.FanMs).coerceIn(0f, 1f))

/** How far tile [index]'s count is through its count-up at [ms], which starts [ShareStripe.CountStart] ms after its fan-out. */
internal fun countProgress(ms: Float, index: Int): Float =
    MotionTokens.EasingEmphasizedDecelerate.transform(
        ((ms - index * RecapMotion.FanStagger - ShareStripe.CountStart) / MotionTokens.CountUpDuration).coerceIn(0f, 1f),
    )

/** How long a hand of [tiles] takes to fan out and count up. */
internal fun handDuration(tiles: Int): Int =
    max(RecapMotion.FanMs, ShareStripe.CountStart + MotionTokens.CountUpDuration) + max(tiles - 1, 0) * RecapMotion.FanStagger

/** Prototype 15 B's numbers, in px at poster scale (one dp is one px on the poster). */
internal object ShareStripe {
    const val TileMinWidth = 128f
    const val TileWidthRange = 44f
    const val WidthCurve = 0.7f
    const val TileAspect = 1.42f
    const val TileGap = 20
    const val TileTilt = 3.2f
    const val TileDrop = 5f
    const val TileRadius = 30

    /** The content width, and the room the row keeps free of tiles for the rings and the tilt. */
    const val RowWidth = 920f
    const val RowSlack = 40f

    /** The plate ring and the ink ring around a tile are each this wide, outside the tile. */
    const val Ring = 4

    /** Above and below the row, from the tiles' edges (the rings sit inside these), and under it when stat tiles follow. */
    const val PadTop = 34
    const val PadBottom = 26
    const val Below = 48

    const val GlyphShare = 0.4f
    const val CountMin = 46
    const val CountShare = 0.3f
    const val CountLine = 1.1f
    const val NameSize = 28
    const val NameLine = 32
    const val NamePad = 8
    const val FaceGap = 4

    // One game: a wide tile, glyph left, count over name right.
    const val WideWidth = 896
    const val WideHeight = 216
    const val WideRadius = 44
    const val WidePad = 12
    const val WideInset = 40
    const val WideGap = 36
    const val WideGlyph = 128
    const val WideCount = 104
    const val WideName = 44
    const val WideNameLine = 48

    // Black lines HatchLine px wide every HatchPeriod px at 135°, then a white gloss from GlossAlpha to none over the top GlossShare.
    const val HatchAlpha = 0.09f
    const val HatchLine = 7f
    const val HatchPeriod = 21f
    const val GlossAlpha = 0.26f
    const val GlossShare = 0.48f

    // Arrival: the stack stands Rise px low at StackScale; a tile fades in over the first FadeShare of its way and counts up
    // CountStart ms after it starts.
    const val Rise = 40f
    const val StackScale = 0.9f
    const val FadeShare = 0.25f
    const val CountStart = 260
}
