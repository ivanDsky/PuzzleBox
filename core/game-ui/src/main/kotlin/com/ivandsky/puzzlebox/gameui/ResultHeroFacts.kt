package com.ivandsky.puzzlebox.gameui

import com.ivandsky.puzzlebox.domain.ResultInsights
import com.ivandsky.puzzlebox.domain.ResultRank
import com.ivandsky.puzzlebox.domain.moments.Moment
import com.ivandsky.puzzlebox.game.BestScore
import com.ivandsky.puzzlebox.game.RankOrder

/**
 * The facts one Results hero page is built from; [rank] and [insights] are what the page reads beyond the old result.
 * [moments] (L1) are the celebrated facts of a counted solve, in any order; the page shows the strongest three. A
 * [challenge] (L4) is the friend the Challenge row names. Both are absent by default and the page is then what it was.
 * [samePuzzle] (M10) is what the player's earlier solves of this very puzzle say; null (the default) keeps the line that
 * compares with the game's best, and any value, even [SamePuzzleSolves.None], draws that line larger.
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
    val moments: List<Moment> = emptyList(),
    val challenge: ResultPrimaryAction.ChallengeFriend? = null,
    val samePuzzle: SamePuzzleSolves? = null,
)
