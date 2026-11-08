package com.ivandsky.puzzlebox.domain

import com.ivandsky.puzzlebox.data.calendar.GameCalendar
import com.ivandsky.puzzlebox.data.repository.DailyRepository
import com.ivandsky.puzzlebox.model.GameId
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf

/**
 * [game]'s archive progress (spec A26/A27): #1 .. today from [DailyRepository.archiveRange],
 * re-read at each of the game's midnights, and its solved days counted from
 * [ObserveGameHistory] with [ObserveGameStats]' filter (an edition-numbered record), so
 * "n of N solved" always matches the stats card. Null for a game without an archive.
 */
class ObserveArchiveProgress(
    private val daily: DailyRepository,
    private val calendar: GameCalendar,
    private val observeGameHistory: ObserveGameHistory,
) {

    operator fun invoke(game: GameId): Flow<ArchiveProgress?> {
        if (daily.archiveRange(game) == null) return flowOf(null)
        return combine(calendar.observeToday(game), observeGameHistory()) { _, days ->
            val range = daily.archiveRange(game) ?: return@combine null
            val solved = days.filter { it.gameId == game && it.isSolvedDaily() }
            ArchiveProgress(
                range = range,
                total = ChronoUnit.DAYS.between(range.start, range.endInclusive).toInt() + 1,
                solved = solved.size,
                archivedSolves = solved.count { it.archived },
            )
        }.distinctUntilChanged()
    }
}
