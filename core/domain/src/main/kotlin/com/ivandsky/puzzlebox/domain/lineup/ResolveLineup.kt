package com.ivandsky.puzzlebox.domain.lineup

import com.ivandsky.puzzlebox.data.match.LegRef
import com.ivandsky.puzzlebox.data.match.Match
import com.ivandsky.puzzlebox.data.match.MatchLeg
import com.ivandsky.puzzlebox.domain.DrawPools
import com.ivandsky.puzzlebox.model.GameId

/**
 * Turns a lineup into the duel's legs, in the lineup's order, in two passes: every exact puzzle is reserved first, so
 * no "Any" entry draws it, then the entries resolve one by one, each "Any" skipping the reserved and the already used
 * puzzles. The first entry that can't be drawn ends the draw as [Draw.Undrawable].
 */
internal class ResolveLineup(private val pools: DrawPools) {

    sealed interface Draw {
        data class Drawn(val legs: List<MatchLeg>) : Draw

        /** [position] counts from 1, as the lineup's rows read. */
        data class Undrawable(val position: Int, val gameId: GameId) : Draw
    }

    /** [entries] must hold 1..[Match.MAX_LEGS]; the UI never sends more. */
    suspend operator fun invoke(entries: List<LineupEntry>, me: String, includeFriends: Boolean): Draw {
        require(entries.size in 1..Match.MAX_LEGS) { "a lineup holds 1..${Match.MAX_LEGS} entries, got ${entries.size}" }
        val reserved = entries.filterIsInstance<ExactPuzzle>().mapTo(mutableSetOf()) { it.ref }
        val context = LineupDrawContext(pools, reserved, used = mutableSetOf<LegRef>(), me, includeFriends)
        val legs = entries.mapIndexed { index, entry ->
            val leg = when (entry) {
                is AnyGame -> entry.resolve(context)
                is ExactPuzzle -> entry.resolve(context)
            } ?: return Draw.Undrawable(index + 1, entry.gameId)
            context.used += leg.ref
            leg
        }
        return Draw.Drawn(legs)
    }
}
