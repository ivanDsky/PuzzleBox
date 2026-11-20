package com.ivandsky.puzzlebox.app.widget

import android.util.Log
import com.ivandsky.puzzlebox.domain.ResultRank
import com.ivandsky.puzzlebox.domain.TodayEntry
import com.ivandsky.puzzlebox.domain.TodayStatus
import com.ivandsky.puzzlebox.game.BestScore
import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.model.GameId
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The scoreboard half of the widget's read: each game's best, the rank of every solved game, which solves are a new best,
 * and when the next puzzles land. The five rank reads run together and each gets [rankLimitMs], so a slow network costs
 * that rank, not the picture. Nothing here fails the read: a best, rank or rollover that cannot be read is simply
 * absent, and only cancelling the caller propagates. A rank is as of this read; nothing refreshes it between draws.
 */
class ReadWidgetScoreboard(
    private val best: suspend (GameId) -> BestScore?,
    private val records: suspend () -> List<CompletionRecord>,
    private val rank: (GameId, LocalDate) -> Flow<ResultRank>,
    private val puzzleDay: () -> LocalDate,
    private val rankOrder: (GameId) -> RankOrder,
    private val nextRollover: () -> Instant,
    private val rankLimitMs: Long = RANK_LIMIT_MS,
) {
    private val isNewBest = IsNewBest()

    suspend operator fun invoke(today: List<TodayEntry>): WidgetScoreboard = coroutineScope {
        val games = today.map { it.game }
        val solved = today.filter { it.status is TodayStatus.Solved }.map { it.game }
        val day = puzzleDay()
        val bests = games.map { game -> async { game to attempt("best of $game") { best(game) } } }
        val ranks = solved.map { game -> async { game to rankOf(game, day) } }
        val solves = if (solved.isEmpty()) null else async { attempt("records") { records() } }

        val records = solves?.await().orEmpty()
        WidgetScoreboard(
            bestMs = bests.awaitAll().mapNotNull { (game, score) -> score?.let { game to it.timeMs } }.toMap(),
            ranks = ranks.awaitAll().toMap(),
            newBest = solved.filterTo(HashSet()) { isNewBest(records, it, day, rankOrder(it)) },
            nextRollover = attempt("next rollover") { nextRollover() },
        )
    }

    // The board's first value is Loading, which the rank shows as Hidden. Hidden is never final in time, so wait past it.
    private suspend fun rankOf(game: GameId, day: LocalDate): WidgetRank {
        val result = attempt("rank of $game") {
            withTimeoutOrNull(rankLimitMs) { rank(game, day).first { it !is ResultRank.Hidden } }
        }
        return when (result) {
            is ResultRank.Ranked -> WidgetRank.Ranked(result.position, result.count)
            ResultRank.SignedOut -> WidgetRank.SignedOut
            else -> WidgetRank.Missing
        }
    }

    private suspend fun <T> attempt(what: String, block: suspend () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "widget $what not read: ${e.message}")
        null
    }

    private companion object {
        const val RANK_LIMIT_MS = 3_000L
        const val TAG = "ReadWidgetScoreboard"
    }
}
