package com.ivandsky.puzzlebox.domain

import com.ivandsky.puzzlebox.model.GameId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * The stats every surface shows (History, Profile, Result), read from the merged history
 * ([ObserveGameHistory]) so they never disagree: [game]'s solved dailies, or every game's
 * when null. Each day's record row counts (A4: the first completion). Practice solves
 * (no edition number) never count, so they move no streak, time or count.
 */
class ObserveGameStats(
    private val observeGameHistory: ObserveGameHistory,
    private val computeStreak: ComputeStreak,
) {

    operator fun invoke(game: GameId? = null): Flow<GameStats> = observeGameHistory()
        .map { days ->
            val solved = days.filter {
                it.record != null && it.puzzleNumber != null && (game == null || it.gameId == game)
            }
            val records = solved.mapNotNull { it.record }
            val solvedDays = solved.map { it.day }
            GameStats(
                currentStreak = computeStreak(solvedDays),
                bestStreak = computeStreak.best(solvedDays),
                solved = records.size,
                bestTimeMs = records.minOfOrNull { it.time },
                hintFree = records.count { it.hints == 0 },
                averageTimeMs = if (records.isEmpty()) null else records.sumOf { it.time } / records.size,
                playingSince = solvedDays.minOrNull(),
            )
        }
        .distinctUntilChanged()
}
