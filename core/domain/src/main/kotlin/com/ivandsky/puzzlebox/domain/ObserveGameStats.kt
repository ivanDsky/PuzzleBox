package com.ivandsky.puzzlebox.domain

import com.ivandsky.puzzlebox.game.BestScore
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.PuzzleKey
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * The stats every surface shows (History, Profile, Result), read from the merged history
 * ([ObserveGameHistory]) so they never disagree: [game]'s solved dailies, or every game's
 * when null. Each day's record row counts (A4: the first completion). Practice solves
 * (no edition number) never count, so they move no streak, time or count. Archived days
 * count as solves but never move a streak, unless an import covers the day: an imported
 * result is the player's real solve of that day. [GameStats.best] ranks by [game]'s
 * [RankOrder]; across every game, time first.
 */
class ObserveGameStats(
    private val registry: GameRegistry,
    private val observeGameHistory: ObserveGameHistory,
    private val computeStreak: ComputeStreak,
) {

    operator fun invoke(game: GameId? = null): Flow<GameStats> {
        val rankOrder = game?.let { registry[it]?.score?.rankOrder } ?: RankOrder.TimeThenHints
        return observeGameHistory().map { days ->
            val solved = days.filter { it.isSolvedDaily() && (game == null || it.gameId == game) }
            val records = solved.mapNotNull { it.record }
            val solvedDays = solved.map { it.day }
            val streakDays = solved.filter { it.isStreakDay() }.map { it.day }
            val ledger = computeStreak(streakDays)
            GameStats(
                currentStreak = ledger.current,
                bestStreak = ledger.best,
                solved = records.size,
                hintFree = records.count { it.hints == 0 },
                averageTimeMs = if (records.isEmpty()) null else records.sumOf { it.time } / records.size,
                playingSince = solvedDays.minOrNull(),
                best = records.map { BestScore(it.time, it.hints) }.minWithOrNull(rankOrder.comparator),
                rankOrder = rankOrder,
                freezes = ledger.freezes,
            )
        }.distinctUntilChanged()
    }
}

/** A day that counts as a solve: its record row, on a numbered daily (practice never counts). */
internal fun GameHistoryDay.isSolvedDaily(): Boolean =
    record != null && PuzzleKey.Daily(day, puzzleNumber).isOfficialDaily()

/**
 * The one "official daily" predicate: a numbered [PuzzleKey.Daily] (practice has no number; custom
 * is never daily), optionally pinned to [on] (a game's today).
 */
internal fun PuzzleKey.isOfficialDaily(on: LocalDate? = null): Boolean =
    this is PuzzleKey.Daily && puzzleNumber != null && (on == null || date == on)

/**
 * A solve that moves a streak. Archived days count as solves but never as streak days, unless
 * a LinkedIn import covers the day: the player really solved it on its own day.
 */
internal fun GameHistoryDay.isStreakDay(): Boolean = isSolvedDaily() && (!archived || fromLinkedIn)
