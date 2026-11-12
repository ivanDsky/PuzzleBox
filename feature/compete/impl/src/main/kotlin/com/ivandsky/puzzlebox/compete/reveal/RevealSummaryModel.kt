package com.ivandsky.puzzlebox.compete.reveal

import com.ivandsky.puzzlebox.data.match.Match
import com.ivandsky.puzzlebox.designsystem.formatDuration
import com.ivandsky.puzzlebox.domain.DuelSettings
import com.ivandsky.puzzlebox.domain.LegBasis
import com.ivandsky.puzzlebox.domain.LegVerdict
import com.ivandsky.puzzlebox.domain.MatchVerdict
import com.ivandsky.puzzlebox.domain.Outcome
import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.gameui.DuelSideState
import com.ivandsky.puzzlebox.model.GameId
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

/** How the duel ended for the viewer. */
internal enum class VerdictKind { Win, Loss, Draw }

/** The three tiles: puzzles won of [puzzles], and the viewer's own fastest time (null with none) and total time. */
internal data class SummaryTiles(val puzzlesWon: Int, val puzzles: Int, val fastestMs: Long?, val totalMs: Long)

/**
 * How far apart a puzzle's two results were, for the margin bar: [ratio] (0..1) of the widest gap in the match (which
 * side it leans to is the row's winner) and the short [label] at the bar's end ("14 s", "2 hints", "tie", "—").
 */
internal data class RowMargin(val ratio: Float, val label: String)

/**
 * One line of the puzzles list: the card's title and basis, each side's time text ("Gave up" or "No result" without
 * one), the [winner] (null when nobody took the puzzle), whether the puzzle was [tied], the [margin], and, in a game
 * that ranks on hints, each side's hint count ([myHints], [theirHints]; null elsewhere). [legIndex] is the puzzle's
 * place in the match, which a compare of its two replays opens by.
 */
internal data class PuzzleRow(
    val gameId: GameId,
    val title: String,
    val basis: String,
    val mine: String,
    val theirs: String,
    val winner: Side?,
    val tied: Boolean,
    val margin: RowMargin,
    val myHints: String? = null,
    val theirHints: String? = null,
    val legIndex: Int,
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
    val myPhotoUrl: String? = null,
    val theirPhotoUrl: String? = null,
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

internal fun puzzleRows(cards: List<RevealCard>): List<PuzzleRow> {
    val widest = cards.maxOfOrNull { it.leg.timeGapSeconds() ?: 0L }?.coerceAtLeast(1L) ?: 1L
    return cards.map { card ->
        val hinted = card.rankOrder == RankOrder.HintsThenTime
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
            margin = card.leg.marginAgainst(widest),
            myHints = card.leg.mine.hintsCaption(hinted),
            theirHints = card.leg.theirs.hintsCaption(hinted),
            legIndex = card.legIndex,
        )
    }
}

private fun DuelSideState.hintsCaption(hinted: Boolean): String? =
    if (hinted && this is DuelSideState.Time) hintsLabel(hints) else null

private fun LegVerdict.decidedBasis(): LegBasis? = when (this) {
    is LegVerdict.Win -> basis
    is LegVerdict.Loss -> basis
    else -> null
}

/** The whole-second gap of a puzzle decided on time between two timed sides; null for any other puzzle. */
private fun LegReveal.timeGapSeconds(): Long? {
    val a = mine as? DuelSideState.Time ?: return null
    val b = theirs as? DuelSideState.Time ?: return null
    return if (verdict.decidedBasis() == LegBasis.Time) marginSeconds(a.timeMs, b.timeMs) else null
}

/** The bar of a puzzle: its time gap against the [widest] one (never under a sliver), a fixed stub for hints, empty for a tie. */
private fun LegReveal.marginAgainst(widest: Long): RowMargin {
    val a = mine as? DuelSideState.Time
    val b = theirs as? DuelSideState.Time
    val gap = timeGapSeconds()
    return when {
        gap != null -> RowMargin(maxOf(MinMarginRatio, gap.toFloat() / widest), "$gap s")
        a != null && b != null && verdict.decidedBasis() == LegBasis.Hints -> RowMargin(HintsMarginRatio, hintsLabel(abs(a.hints - b.hints)))
        a != null && b != null && verdict == LegVerdict.Draw -> RowMargin(0f, "tie")
        verdict.decidedBasis() != null -> RowMargin(1f, "—")
        else -> RowMargin(0f, "—")
    }
}

private const val MinMarginRatio = 0.12f
private const val HintsMarginRatio = 0.4f

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
    myPhotoUrl = plan.me.photoUrl,
    theirPhotoUrl = plan.opponent.photoUrl,
)
