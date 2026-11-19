package com.ivandsky.puzzlebox.resultui

import com.ivandsky.puzzlebox.designsystem.formatDuration
import com.ivandsky.puzzlebox.game.BestScore
import com.ivandsky.puzzlebox.game.RankOrder
import kotlin.math.abs

private fun shownAlike(a: BestScore, b: BestScore, order: RankOrder): Boolean = when (order) {
    RankOrder.TimeThenHints -> a.timeMs / 1000 == b.timeMs / 1000
    RankOrder.HintsThenTime -> a.hints == b.hints && a.timeMs / 1000 == b.timeMs / 1000
}

/** One labelled number of the compare row: the small upper-case [label] over its [value]. */
internal data class HeroCompareColumn(val label: String, val value: String)

/** How the gap tag is coloured: [Better] in the game's colour, [Level] and [Worse] on the neutral container. */
internal enum class HeroGapTone { Better, Level, Worse }

/** The gap pill beside the previous value: "−12s", "+1s", "+1:12", "same", or "−1 hint" / "+1 hint" in hints. */
internal data class HeroGapTag(val text: String, val tone: HeroGapTone)

/**
 * The hero's compare row: the PREVIOUS column ("LINKEDIN" for a LinkedIn solve), its gap tag and, only when the best of the
 * earlier solves shows differently, the BEST column. A hints-first game ([order]) counts hints when they differ from the
 * previous solve's, and the time otherwise. Times compare at the resolution [formatDuration] shows, in whole seconds.
 */
internal data class HeroCompare(val previous: HeroCompareColumn, val tag: HeroGapTag, val best: HeroCompareColumn?)

private const val MINUS = "\u2212"

internal fun heroSamePuzzleCompare(
    solves: SamePuzzleSolves,
    order: RankOrder,
    timeMs: Long,
    hintsUsed: Int,
): HeroCompare? {
    val previous = solves.previous ?: return null
    val byHints = order == RankOrder.HintsThenTime && hintsUsed != previous.hints
    val shown: (BestScore) -> String = if (byHints) { score -> heroHints(score.hints) } else { score -> formatDuration(score.timeMs) }
    val best = solves.best?.takeIf { if (byHints) it.hints != previous.hints else !shownAlike(previous, it, order) }
    return HeroCompare(
        previous = HeroCompareColumn(if (solves.previousFromLinkedIn) "LINKEDIN" else "PREVIOUS", shown(previous)),
        tag = if (byHints) hintsTag(hintsUsed - previous.hints) else timeTag(timeMs, previous.timeMs),
        best = best?.let { HeroCompareColumn("BEST", shown(it)) },
    )
}

private fun timeTag(timeMs: Long, previousMs: Long): HeroGapTag = when {
    timeMs / 1000 == previousMs / 1000 -> HeroGapTag("same", HeroGapTone.Level)
    timeMs < previousMs -> HeroGapTag("$MINUS${heroGap(previousMs, timeMs)}", HeroGapTone.Better)
    else -> HeroGapTag("+${heroGap(previousMs, timeMs)}", HeroGapTone.Worse)
}

private fun hintsTag(difference: Int): HeroGapTag {
    val count = abs(difference)
    val noun = if (count == 1) "hint" else "hints"
    return if (difference < 0) HeroGapTag("$MINUS$count $noun", HeroGapTone.Better) else HeroGapTag("+$count $noun", HeroGapTone.Worse)
}
