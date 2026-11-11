package com.ivandsky.puzzlebox.compete.reveal

import com.ivandsky.puzzlebox.data.match.LegRef
import com.ivandsky.puzzlebox.data.match.OutboxResult
import com.ivandsky.puzzlebox.data.match.Player
import com.ivandsky.puzzlebox.data.match.ResultPostState
import com.ivandsky.puzzlebox.domain.LegBasis
import com.ivandsky.puzzlebox.domain.LegSeen
import com.ivandsky.puzzlebox.domain.LegVerdict
import com.ivandsky.puzzlebox.domain.MatchView
import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.gameui.DuelSideState
import com.ivandsky.puzzlebox.model.GameId
import java.time.Instant
import kotlin.math.abs

/**
 * One puzzle of the race as the screen draws it: the machine's [leg], the card's [title], the short [chip] the tablet
 * strip shows beside the game glyph, the game's [rankOrder] (which figure `DuelCompare` leads with), the [basis] line
 * under the sides and the [pill] naming who took it.
 */
internal data class RevealCard(
    val leg: LegReveal,
    val title: String,
    val chip: String,
    val rankOrder: RankOrder,
    val basis: String,
    val pill: String,
)

/** The reveal's cast and its race: [cards] holds the settled puzzles only, in puzzle order. */
internal data class RevealPlan(val me: Player, val opponent: Player, val cards: List<RevealCard>) {
    val legs: List<LegReveal> get() = cards.map { it.leg }
}

/**
 * Maps [view] to the reveal's plan. My side comes from my outbox rows that count (Pending or Posted, as the outcome
 * counts them), the opponent's from [MatchView.theirs], and each verdict from [MatchView.outcome]: the domain rule
 * decides, nothing here compares times. A puzzle whose verdict is still Pending never enters the race.
 */
internal fun revealPlan(view: MatchView, nameOf: (GameId) -> String, rankOrderOf: (GameId) -> RankOrder): RevealPlan {
    val match = view.match
    val me = (if (match.challenger.uid == view.me) match.challenger else match.opponent) ?: Player(view.me, "You", null)
    val opponent = match.opponentOf(view.me) ?: Player("", "Opponent", null)
    val mine = view.mine.filter { it.result.uid == view.me && it.state.counts() }.associateBy { it.result.legIndex }
    val cards = match.legs.mapIndexedNotNull { index, leg ->
        val verdict = view.outcome.legs.getOrNull(index)
        if (verdict == null || verdict == LegVerdict.Pending) return@mapIndexedNotNull null
        val gameId = leg.ref.gameId
        val order = rankOrderOf(gameId)
        val mySide = mySide(mine[index])
        val theirSide = theirSide(view.theirs.getOrNull(index))
        val ref = leg.ref
        RevealCard(
            leg = LegReveal(gameId, mySide, theirSide, verdict),
            title = when (ref) {
                is LegRef.Daily -> "${nameOf(gameId)} #${ref.number}"
                is LegRef.Custom -> "Friend's puzzle"
            },
            chip = when (ref) {
                is LegRef.Daily -> "#${ref.number}"
                is LegRef.Custom -> "Friend's"
            },
            rankOrder = order,
            basis = basisLine(verdict, mySide, theirSide, order, opponent.name),
            pill = pillText(verdict, me.name, opponent.name),
        )
    }
    return RevealPlan(me, opponent, cards)
}

private fun ResultPostState.counts() = this == ResultPostState.Pending || this == ResultPostState.Posted

private fun mySide(row: OutboxResult?): DuelSideState = when {
    row == null -> DuelSideState.NotPlayed
    row.result.gaveUp -> DuelSideState.GaveUp
    else -> DuelSideState.Time(row.result.timeMs, row.result.hints)
}

private fun theirSide(seen: LegSeen?): DuelSideState = when (seen) {
    null, LegSeen.NotPlayed -> DuelSideState.NotPlayed
    LegSeen.Played -> DuelSideState.Sealed
    is LegSeen.Revealed -> if (seen.result.gaveUp) DuelSideState.GaveUp else DuelSideState.Time(seen.result.timeMs, seen.result.hints)
}

private fun pillText(verdict: LegVerdict, me: String, them: String): String = when (verdict) {
    is LegVerdict.Win -> "$me wins"
    is LegVerdict.Loss -> "$them wins"
    LegVerdict.Draw -> "Tie"
    LegVerdict.Void, LegVerdict.Pending -> "No point"
}

private fun basisLine(verdict: LegVerdict, mine: DuelSideState, theirs: DuelSideState, order: RankOrder, opponent: String) =
    when (verdict) {
        is LegVerdict.Win -> decidedLine(verdict.basis, mine, theirs, viewerWon = true, opponent = opponent)
        is LegVerdict.Loss -> decidedLine(verdict.basis, theirs, mine, viewerWon = false, opponent = opponent)
        LegVerdict.Draw -> drawLine(mine, theirs, order)
        LegVerdict.Void, LegVerdict.Pending -> "Doesn't count for either player"
    }

/** The line for a puzzle [winner] took over [loser], worded from the viewer's side: [viewerWon] says which one I am. */
private fun decidedLine(basis: LegBasis, winner: DuelSideState, loser: DuelSideState, viewerWon: Boolean, opponent: String): String {
    val verdictTail = if (viewerWon) "you win this puzzle" else "$opponent wins this puzzle"
    val loserName = if (viewerWon) opponent else "You"
    if (winner !is DuelSideState.Time) return "Counts for ${if (viewerWon) "you" else opponent}"
    return when (loser) {
        is DuelSideState.Time -> when (basis) {
            LegBasis.Time -> "${marginSeconds(winner.timeMs, loser.timeMs)} s faster"
            LegBasis.Hints -> "Fewer hints · ${winner.hints} vs ${loser.hints}"
        }
        DuelSideState.Sealed -> "$opponent's result isn't available · $verdictTail"
        DuelSideState.NotPlayed -> "$loserName didn't play · $verdictTail"
        DuelSideState.GaveUp -> "$loserName gave up · $verdictTail"
    }
}

private fun drawLine(mine: DuelSideState, theirs: DuelSideState, order: RankOrder): String = when {
    mine == DuelSideState.GaveUp && theirs == DuelSideState.GaveUp -> "Both gave up · no point"
    mine !is DuelSideState.Time || theirs !is DuelSideState.Time -> "No result from either player · no point"
    order == RankOrder.TimeThenHints -> "Same whole second · no point"
    else -> "Same hints, same whole second · no point"
}

/** The gap in whole seconds, each time floored first: what the figures show and the outcome decides on (59.0 vs 60.9 is "1 s"). */
internal fun marginSeconds(aMs: Long, bMs: Long): Long = abs(aMs / MS_PER_SECOND - bMs / MS_PER_SECOND)

private const val MS_PER_SECOND = 1_000L

/**
 * How the reveal opens: [startAtEnd] skips the replay (reduced motion, or the viewer has seen it), and [markSeen]
 * is true until the seen marker is written, so only the first opening writes it.
 */
internal data class RevealStart(val startAtEnd: Boolean, val markSeen: Boolean) {
    companion object {
        fun of(reducedMotion: Boolean, revealSeenAt: Instant?) =
            RevealStart(startAtEnd = reducedMotion || revealSeenAt != null, markSeen = revealSeenAt == null)
    }
}
