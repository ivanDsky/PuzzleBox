package com.ivandsky.puzzlebox.gameui

import com.ivandsky.puzzlebox.domain.ResultInsights
import com.ivandsky.puzzlebox.domain.ResultRank
import com.ivandsky.puzzlebox.game.BestScore
import com.ivandsky.puzzlebox.game.RankOrder

/** The facts one Results hero page is built from; [rank] and [insights] are what the page reads beyond the old result. */
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
)
