package com.ivandsky.puzzlebox.domain.lineup

import com.ivandsky.puzzlebox.data.match.LegRef
import com.ivandsky.puzzlebox.domain.DrawPools

/**
 * What one lineup draw shares: the [pools] to pick from, the [reserved] refs of every exact entry, the refs [used] by
 * the entries resolved so far, [me] (whose own puzzles never come back) and whether friends' puzzles may be drawn.
 */
internal class LineupDrawContext(
    val pools: DrawPools,
    val reserved: Set<LegRef>,
    val used: MutableSet<LegRef>,
    val me: String,
    val includeFriends: Boolean,
)
