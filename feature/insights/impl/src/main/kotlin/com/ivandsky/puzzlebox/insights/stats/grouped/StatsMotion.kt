package com.ivandsky.puzzlebox.insights.stats.grouped

import androidx.compose.runtime.compositionLocalOf

/**
 * The calm motion of the grouped stats page (ADR-0022: the StatsFilters flag is read at the view model and the page
 * below it is told by what it is given). The grouped list provides one of these, so every chart and headline number
 * below it moves; with none provided (the flag-off page, a plain section preview) they draw at rest.
 *
 * It also remembers which blocks have already arrived: a lazy list drops a block that scrolls away and composes it
 * again on the way back, and the arrival must not replay then, nor on a Since or game change, which morph instead.
 */
internal class StatsMotion {
    private val arrived = mutableSetOf<Any>()

    /**
     * False while the page's first blocks are composing, true once they are on screen: only blocks that arrive together
     * wait on each other (the arrival stagger), a block composed later, on a scroll, starts at once.
     */
    var firstPassDone = false

    /** Whether [key] has arrived already. */
    fun hasArrived(key: Any): Boolean = key in arrived

    /** True once for [key]: the first block to ask arrives, every later one (the same block again) is already there. */
    fun claimArrival(key: Any): Boolean = arrived.add(key)
}

internal val LocalStatsMotion = compositionLocalOf<StatsMotion?> { null }
