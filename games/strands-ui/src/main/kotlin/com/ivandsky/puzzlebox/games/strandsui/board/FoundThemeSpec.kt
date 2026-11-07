package com.ivandsky.puzzlebox.games.strandsui.board

import androidx.compose.animation.core.Easing
import androidx.compose.ui.graphics.Color
import com.ivandsky.puzzlebox.designsystem.BoardColors
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.games.strands.render.StrandsCell
import com.ivandsky.puzzlebox.games.strands.Coord
import com.ivandsky.puzzlebox.games.strands.render.LinkKind
import com.ivandsky.puzzlebox.games.strands.render.StrandsLink
import com.ivandsky.puzzlebox.games.strandsui.FoundEvent
import com.ivandsky.puzzlebox.games.strandsui.StrandsCopy
import com.ivandsky.puzzlebox.games.strandsui.StrandsMotion

/**
 * A found theme word: the pink token (magenta for every second word, [altThemeLinks]), and
 * the settle as it is found. Letter n waits n × [StrandsMotion.SettleStaggerMs], turns from
 * the trace colour to its own, and runs NYT's
 * `Pulser bounce`, both per [StrandsMotion.FoundSettle].
 */
internal object FoundThemeSpec : StrandsCellSpec {
    private val Settle = StrandsMotion.FoundSettle
    private val BounceAt = floatArrayOf(0f, 0.25f, 0.5f, 0.75f, 1f)
    private val BounceScale = floatArrayOf(
        1f,
        StrandsMotion.SettleDipScale,
        StrandsMotion.SettleOvershootScale,
        StrandsMotion.SettleReboundScale,
        1f,
    )

    override fun fill(colors: BoardColors): Color = colors.strandsThemeFill

    override fun letter(colors: BoardColors): Color = colors.strandsTileText

    override val stateWord: String = StrandsCopy.STATE_THEME

    override fun trigger(cell: StrandsCell, found: FoundEvent?): StrandsMotionTrigger? = foundTrigger(cell, found)

    /** The word just found moves; a letter of an older word, or a restored board, rests. */
    fun foundTrigger(cell: StrandsCell, found: FoundEvent?): StrandsMotionTrigger? {
        val order = found?.cells?.indexOf(cell.coord)?.takeIf { it >= 0 } ?: return null
        return StrandsMotionTrigger(key = found.seq, cue = StrandsMotionCue(order, found.cells.size))
    }

    override fun motionMs(cue: StrandsMotionCue): Int = delayMs(cue) + Settle.enterDurationMillis

    override fun frame(elapsedMs: Int, cue: StrandsMotionCue, reduceMotion: Boolean): StrandsCellFrame =
        settle(elapsedMs - delayMs(cue), reduceMotion)

    /**
     * One letter's bounce [localMs] after its own start, eased by [easing] (the spangram
     * sweep passes its own); before it, the letter still shows the trace colour.
     */
    fun settle(localMs: Int, reduceMotion: Boolean, easing: Easing = Settle.enterEasing): StrandsCellFrame = when {
        reduceMotion || localMs >= Settle.enterDurationMillis -> StrandsCellFrame.Rest
        localMs < 0 -> StrandsCellFrame(lit = 0f)
        else -> StrandsCellFrame(
            scale = keyframes(localMs.toFloat() / Settle.enterDurationMillis, BounceAt, BounceScale, easing),
            lit = (localMs.toFloat() / MotionTokens.DurationShort).coerceAtMost(1f),
        )
    }

    private fun delayMs(cue: StrandsMotionCue): Int = cue.order * StrandsMotion.SettleStaggerMs
}

/** Found theme words alternate pink and magenta (A14): every second theme link, in found order, takes the alt fill. */
internal fun altThemeLinks(links: List<StrandsLink>): Set<StrandsLink> =
    links.filter { it.kind == LinkKind.Theme }.filterIndexedTo(HashSet()) { index, _ -> index % 2 == 1 }

internal fun Set<StrandsLink>.altThemeCells(): Set<Coord> = flatMapTo(HashSet()) { it.cells }
