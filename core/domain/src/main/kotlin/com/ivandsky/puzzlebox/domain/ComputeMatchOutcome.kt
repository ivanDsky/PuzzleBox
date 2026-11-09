package com.ivandsky.puzzlebox.domain

import com.ivandsky.puzzlebox.data.match.LegResult
import com.ivandsky.puzzlebox.data.match.Match
import com.ivandsky.puzzlebox.game.BestScore
import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.model.GameId
import java.time.Instant

/**
 * The one rule for who won a duel (spec A2, A3, A10): per-leg verdicts and the match verdict as [me] (either
 * participant) sees them, from the results seen so far and [now]. Pure: no clock, no I/O.
 *
 * A result counts only when completed at or before the deadline. A leg compares whole seconds under its game's
 * [RankOrder]; giving up is not finishing; a leg authored by either player is void. A leg missing a result stays
 * pending until the match closes, then goes against whoever never finished. A cancelled, declined or unclaimed match
 * is void throughout, except that an unclaimed one is pending until its deadline.
 */
object ComputeMatchOutcome {

    private const val MS_PER_SECOND = 1_000L

    operator fun invoke(
        match: Match,
        results: List<LegResult>,
        me: String,
        now: Instant,
        rankOrderOf: (GameId) -> RankOrder,
    ): Outcome {
        val opponent = match.opponent
        if (match.cancelled || match.declined || (opponent == null && now >= match.deadline)) {
            return Outcome(match.legs.map { LegVerdict.Void }, 0, 0, settled = true, verdict = null)
        }
        if (opponent == null) {
            return Outcome(match.legs.map { LegVerdict.Pending }, 0, 0, settled = false, verdict = null)
        }
        val them = if (me == opponent.uid) match.challenger.uid else opponent.uid
        val players = setOf(match.challenger.uid, opponent.uid)
        val deadlineMs = match.deadline.toEpochMilli()
        val counted = results.filter { it.completedAtMs <= deadlineMs }
        val closed = now >= match.closesAt
        val legs = match.legs.mapIndexed { index, leg ->
            if (leg.authorUid in players) {
                LegVerdict.Void
            } else {
                val order = rankOrderOf(leg.ref.gameId)
                legVerdict(counted.resultOf(me, index), counted.resultOf(them, index), order, closed)
            }
        }
        val myWins = legs.count { it is LegVerdict.Win }
        val theirWins = legs.count { it is LegVerdict.Loss }
        val settled = legs.none { it == LegVerdict.Pending }
        return Outcome(legs, myWins, theirWins, settled, if (settled) matchVerdict(myWins, theirWins) else null)
    }

    private fun List<LegResult>.resultOf(uid: String, leg: Int): LegResult? =
        firstOrNull { it.uid == uid && it.legIndex == leg }

    /** A gave-up result is final but unfinished; a missing one can still arrive until close. */
    private fun legVerdict(mine: LegResult?, theirs: LegResult?, order: RankOrder, closed: Boolean): LegVerdict {
        val myScore = mine?.score()
        val theirScore = theirs?.score()
        return when {
            !closed && (mine == null || theirs == null) -> LegVerdict.Pending
            myScore != null && theirScore != null -> compare(myScore, theirScore, order)
            myScore != null -> LegVerdict.Win(order.keys.first())
            theirScore != null -> LegVerdict.Loss(order.keys.first())
            else -> LegVerdict.Draw
        }
    }

    /** The displayed whole-second score; null for a player who gave up. */
    private fun LegResult.score(): BestScore? =
        if (gaveUp) null else BestScore(timeMs / MS_PER_SECOND * MS_PER_SECOND, hints)

    private fun compare(mine: BestScore, theirs: BestScore, order: RankOrder): LegVerdict {
        val basis = order.keys.firstOrNull { it.valueOf(mine) != it.valueOf(theirs) } ?: return LegVerdict.Draw
        return if (order.comparator.compare(mine, theirs) < 0) LegVerdict.Win(basis) else LegVerdict.Loss(basis)
    }

    private val RankOrder.keys: List<LegBasis>
        get() = when (this) {
            RankOrder.TimeThenHints -> listOf(LegBasis.Time, LegBasis.Hints)
            RankOrder.HintsThenTime -> listOf(LegBasis.Hints, LegBasis.Time)
        }

    private fun LegBasis.valueOf(score: BestScore): Long = when (this) {
        LegBasis.Time -> score.timeMs
        LegBasis.Hints -> score.hints.toLong()
    }

    private fun matchVerdict(myWins: Int, theirWins: Int): MatchVerdict = when {
        myWins > theirWins -> MatchVerdict.Won
        myWins < theirWins -> MatchVerdict.Lost
        else -> MatchVerdict.Drawn
    }
}
