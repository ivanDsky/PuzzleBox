package com.ivandsky.puzzlebox.compete.reveal

import com.ivandsky.puzzlebox.data.match.Match
import com.ivandsky.puzzlebox.designsystem.formatDuration
import com.ivandsky.puzzlebox.domain.DuelSettings
import com.ivandsky.puzzlebox.domain.LegVerdict
import com.ivandsky.puzzlebox.domain.MatchVerdict
import com.ivandsky.puzzlebox.domain.Outcome
import com.ivandsky.puzzlebox.gameui.DuelSideState
import com.ivandsky.puzzlebox.model.GameId
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** How the duel ended for the viewer. */
internal enum class VerdictKind { Win, Loss, Draw }

/** The three tiles: puzzles won of [puzzles], and the viewer's own fastest time (null with none) and total time. */
internal data class SummaryTiles(val puzzlesWon: Int, val puzzles: Int, val fastestMs: Long?, val totalMs: Long)

/**
 * One line of the puzzles list: the card's title and basis, each side's time text ("Gave up" or "No result" without
 * one), the [winner] (null when nobody took the puzzle) and whether the puzzle was [tied].
 */
internal data class PuzzleRow(
    val gameId: GameId,
    val title: String,
    val basis: String,
    val mine: String,
    val theirs: String,
    val winner: Side?,
    val tied: Boolean,
)

/** Everything the summary draws and shares, from the viewer's side. */
internal data class DuelSummary(
    val me: String,
    val opponent: String,
    val kind: VerdictKind,
    val myScore: Int,
    val theirScore: Int,
    val bestOf: String,
    val tiles: SummaryTiles,
    val rows: List<PuzzleRow>,
    val shareText: String,
) {
    val pill: String get() = pillText(kind, opponent)
    val verdict: String get() = verdictText(kind, opponent, myScore, theirScore)
}

/** The match verdict decides; with none yet (an unsettled duel) the scores in hand do. */
internal fun verdictKind(verdict: MatchVerdict?, myWins: Int, theirWins: Int): VerdictKind = when (verdict) {
    MatchVerdict.Won -> VerdictKind.Win
    MatchVerdict.Lost -> VerdictKind.Loss
    MatchVerdict.Drawn -> VerdictKind.Draw
    null -> when {
        myWins > theirWins -> VerdictKind.Win
        myWins < theirWins -> VerdictKind.Loss
        else -> VerdictKind.Draw
    }
}

internal fun pillText(kind: VerdictKind, opponent: String): String = when (kind) {
    VerdictKind.Win -> "You win"
    VerdictKind.Loss -> "$opponent wins"
    VerdictKind.Draw -> "Draw"
}

internal fun verdictText(kind: VerdictKind, opponent: String, mine: Int, theirs: Int): String = when (kind) {
    VerdictKind.Win -> "You take it $mine–$theirs"
    VerdictKind.Loss -> "$opponent takes it $mine–$theirs"
    VerdictKind.Draw -> "Dead even $mine–$theirs"
}

/** "Best of {count} · {Mon 9 Nov}": the day the duel was created, in [zone]. */
internal fun bestOfLine(count: Int, createdAt: Instant, zone: ZoneId, locale: Locale): String =
    "Best of $count · ${DateTimeFormatter.ofPattern("EEE d MMM", locale).format(createdAt.atZone(zone))}"

/** Puzzles I won, and my times only: a missing or given-up result counts for neither the fastest nor the total. */
internal fun summaryTiles(legs: List<LegReveal>): SummaryTiles {
    val times = legs.mapNotNull { (it.mine as? DuelSideState.Time)?.timeMs }
    return SummaryTiles(
        puzzlesWon = legs.count { it.verdict is LegVerdict.Win },
        puzzles = legs.size,
        fastestMs = times.minOrNull(),
        totalMs = times.sum(),
    )
}

internal fun puzzleRows(cards: List<RevealCard>): List<PuzzleRow> = cards.map { card ->
    PuzzleRow(
        gameId = card.leg.gameId,
        title = card.title,
        basis = card.basis,
        mine = timeText(card.leg.mine),
        theirs = timeText(card.leg.theirs),
        winner = when (card.leg.verdict) {
            is LegVerdict.Win -> Side.Mine
            is LegVerdict.Loss -> Side.Theirs
            else -> null
        },
        tied = card.leg.verdict == LegVerdict.Draw,
    )
}

private fun timeText(side: DuelSideState): String = when (side) {
    is DuelSideState.Time -> formatDuration(side.timeMs)
    DuelSideState.GaveUp -> GAVE_UP
    DuelSideState.NotPlayed, DuelSideState.Sealed -> NO_RESULT
}

internal const val GAVE_UP = "Gave up"
internal const val NO_RESULT = "No result"

/** "PuzzleBox · Ivan 2–1 Sam 🟩🟥🟩 · link": one square per puzzle in order, ⬜ for a draw or a void one. */
internal fun shareText(me: String, opponent: String, myWins: Int, theirWins: Int, verdicts: List<LegVerdict>, link: String): String {
    val squares = verdicts.joinToString("") {
        when (it) {
            is LegVerdict.Win -> "🟩"
            is LegVerdict.Loss -> "🟥"
            else -> "⬜"
        }
    }
    return "PuzzleBox · $me $myWins–$theirWins $opponent $squares · $link"
}

/** The same duel again with a fresh draw: same count, games, deadline and friends toggle; never today-only or pasted codes. */
internal fun rematchSettings(match: Match) = DuelSettings(
    count = match.legs.size,
    games = match.legs.mapTo(LinkedHashSet()) { it.ref.gameId },
    todayOnly = false,
    deadlineHours = match.deadlineHours,
    includeFriends = match.includeFriends,
    pastedCodes = emptyList(),
)

/** The summary of [match] as the [plan] and the domain's [outcome] settled it. */
internal fun duelSummary(match: Match, plan: RevealPlan, outcome: Outcome, zone: ZoneId, locale: Locale) = DuelSummary(
    me = plan.me.name,
    opponent = plan.opponent.name,
    kind = verdictKind(outcome.verdict, outcome.myWins, outcome.theirWins),
    myScore = outcome.myWins,
    theirScore = outcome.theirWins,
    bestOf = bestOfLine(plan.cards.size, match.createdAt, zone, locale),
    tiles = summaryTiles(plan.legs),
    rows = puzzleRows(plan.cards),
    shareText = shareText(
        plan.me.name, plan.opponent.name, outcome.myWins, outcome.theirWins, plan.legs.map { it.verdict }, match.code.matchLink,
    ),
)
