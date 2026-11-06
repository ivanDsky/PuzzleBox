package com.ivandsky.puzzlebox.domain

import com.ivandsky.puzzlebox.data.calendar.GameCalendar
import com.ivandsky.puzzlebox.data.repository.CompletionRepository
import com.ivandsky.puzzlebox.data.repository.DailyRepository
import com.ivandsky.puzzlebox.data.repository.SavedPlayRepository
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.PlaySlot
import com.ivandsky.puzzlebox.model.PuzzleKey
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf

/**
 * Every day of [YearMonth] inside [game]'s archive range ([DailyRepository.archiveRange],
 * #1 .. today), ascending, from local data only (spec A27). Status follows
 * this precedence, Solved > InProgress > Stored > NeedsConnection, over one
 * combined flow of daily completion records (a custom record fills no cell, A12),
 * in-progress slots and the game's captured history;
 * the game's day ticks it, so today joins the month at the game's midnight. A game without
 * an archive has no days.
 *
 * Stored still asks [DailyRepository.cachedPuzzle], but only for captured, unplayed days of
 * the month: a past placeholder row (A10) is indistinguishable from a real one in history.
 * The number is the source's ([DailyRepository.puzzleNumber]), else the day's position
 * from #1.
 */
class ObserveArchiveMonth(
    private val daily: DailyRepository,
    private val saved: SavedPlayRepository,
    private val completions: CompletionRepository,
    private val calendar: GameCalendar,
) {

    operator fun invoke(game: GameId, month: YearMonth): Flow<List<ArchiveDay>> {
        if (daily.archiveRange(game) == null) return flowOf(emptyList())
        return combine(
            calendar.observeToday(game),
            completions.observeRecords(),
            saved.observeInProgress(),
            daily.observeHistory(game),
        ) { _, records, slots, entries ->
            val range = daily.archiveRange(game) ?: return@combine emptyList()
            val first = maxOf(month.atDay(1), range.start)
            val last = minOf(month.atEndOfMonth(), range.endInclusive)
            if (first > last) return@combine emptyList()
            val solved = records.filter { it.puzzleRef.gameId == game && it.puzzleRef.key is PuzzleKey.Daily }.groupBy { it.puzzleRef.date }
            val captured = entries.associateBy { it.day }
            (0..ChronoUnit.DAYS.between(first, last)).map { offset ->
                val day = first.plusDays(offset)
                val record = solved[day]?.first()
                val elapsed = slots[PlaySlot(game, day)]
                val entry = captured[day]
                val status = when {
                    record != null -> PastDayStatus.Solved(record.time)
                    elapsed != null -> PastDayStatus.InProgress(elapsed)
                    entry != null && daily.cachedPuzzle(game, day) != null -> PastDayStatus.Stored
                    else -> PastDayStatus.NeedsConnection
                }
                ArchiveDay(
                    date = day,
                    number = daily.puzzleNumber(game, day) ?: position(range.start, day),
                    status = status,
                    archived = entry?.archived == true,
                    openedOn = entry?.let { calendar.dayAt(game, it.fetchedAtMs) },
                )
            }
        }.distinctUntilChanged()
    }

    private fun position(start: LocalDate, day: LocalDate): Int = ChronoUnit.DAYS.between(start, day).toInt() + 1
}
