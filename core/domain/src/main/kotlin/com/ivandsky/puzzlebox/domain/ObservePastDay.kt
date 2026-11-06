package com.ivandsky.puzzlebox.domain

import com.ivandsky.puzzlebox.data.repository.CompletionRepository
import com.ivandsky.puzzlebox.data.repository.DailyRepository
import com.ivandsky.puzzlebox.data.repository.SavedPlayRepository
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.PlaySlot
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * One past day's number and status (spec A20), from local data only, shaped like
 * [ObserveTodaySet]. Solved = the completion record for (game, day); InProgress = a saved
 * play in that slot; Stored = a captured puzzle [DailyRepository.cachedPuzzle] reads back
 * (Room only, so a past day's placeholder row is not Stored, A10); NeedsConnection otherwise.
 * Never uses `day`, which may fetch (archive range). [DailyRepository.observeHistory] drives
 * re-evaluation, so the status updates when the real puzzle replaces a placeholder.
 * The number is [DailyRepository.puzzleNumber], falling back to the stored entry's number
 * for sources without their own numbering.
 */
class ObservePastDay(
    private val daily: DailyRepository,
    private val saved: SavedPlayRepository,
    private val completions: CompletionRepository,
) {

    operator fun invoke(game: GameId, day: LocalDate): Flow<PastDay> = combine(
        completions.observeRecords(),
        saved.observeInProgress(),
        daily.observeHistory(game),
    ) { records, slots, entries ->
        val solved = records.firstOrNull { it.puzzleRef.gameId == game && it.puzzleRef.date == day }
        val elapsed = slots[PlaySlot(game, day)]
        val entry = entries.firstOrNull { it.day == day }
        val status = when {
            solved != null -> PastDayStatus.Solved(solved.time)
            elapsed != null -> PastDayStatus.InProgress(elapsed)
            entry != null && daily.cachedPuzzle(game, day) != null -> PastDayStatus.Stored
            else -> PastDayStatus.NeedsConnection
        }
        PastDay(game, day, daily.puzzleNumber(game, day) ?: entry?.puzzleNumber, status)
    }.distinctUntilChanged()
}
