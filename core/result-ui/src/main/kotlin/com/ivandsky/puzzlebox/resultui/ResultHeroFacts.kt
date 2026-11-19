package com.ivandsky.puzzlebox.resultui

import com.ivandsky.puzzlebox.domain.ResultInsights
import com.ivandsky.puzzlebox.domain.ResultRank
import com.ivandsky.puzzlebox.domain.moments.Moment
import com.ivandsky.puzzlebox.game.BestScore
import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.gameui.RecordVerdict

/**
 * The facts one Results hero page is built from; [rank] and [insights] are what the page reads beyond the old result.
 * [moments] (L1) are the celebrated facts of a counted solve, in any order; the page shows the strongest three. A
 * [challenge] (L4) is the friend the Challenge row names; both are empty for a page that has none (a duel leg's).
 * [samePuzzle] (M10) is what the player's earlier solves of this very puzzle say; null (a duel leg) keeps the line that
 * compares with the game's best, and any value, even [SamePuzzleSolves.None], draws that line larger.
 * [community], when given, makes this a community puzzle's hero: Hints reads "0" rather than "None" and its chips follow it
 * in place of the Today and Average chips.
 * [firstSolveNoGap] (#1762): a first solve of this puzzle that is not a new best leads with its rank among the player's
 * solves of the game rather than the gap to their best; the view model reads the release flag.
 */
data class ResultHeroFacts(
    val timeMs: Long,
    val hintsUsed: Int,
    val verdict: RecordVerdict?,
    val previousBest: BestScore?,
    val rankOrder: RankOrder,
    val game: String,
    val puzzleNumber: Int?,
    val practice: Boolean,
    val playAgain: Boolean,
    val dateLine: String,
    val doneToday: Int?,
    val total: Int?,
    val insights: ResultInsights?,
    val rank: ResultRank,
    val primary: ResultPrimaryAction?,
    val moments: List<Moment>,
    val challenge: ResultPrimaryAction.ChallengeFriend?,
    val samePuzzle: SamePuzzleSolves?,
    val community: ResultCommunity? = null,
    val firstSolveNoGap: Boolean = false,
)
