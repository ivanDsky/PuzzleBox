package com.ivandsky.puzzlebox.play

import com.ivandsky.puzzlebox.data.calendar.GameCalendar
import com.ivandsky.puzzlebox.data.repository.CompletionRepository
import com.ivandsky.puzzlebox.data.repository.DailyRepository
import com.ivandsky.puzzlebox.game.PuzzleRef
import com.ivandsky.puzzlebox.model.PlaySlot
import com.ivandsky.puzzlebox.model.PuzzleKey
import com.ivandsky.puzzlebox.resultui.ResultPrimaryAction
import com.ivandsky.puzzlebox.resultui.SamePuzzleSolves
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf

/**
 * A past day's primary step (M10-5): the same game on the next unsolved day ([nextArchiveDay]),
 * else Done. A solve of a day that already had an earlier solve (a play again, per [earlier]) and a practice puzzle keep
 * Done, as does a game with no archive. [gameName] is the game's display name.
 */
internal class ResultArchiveNext(
    private val daily: DailyRepository,
    private val completions: CompletionRepository,
    private val calendar: GameCalendar,
    private val earlier: ResultSamePuzzle,
    private val slot: PlaySlot.Daily,
    private val gameName: String,
) {
    /** [ref] is the slot's puzzle, known once the history gives its row. */
    fun of(ref: PuzzleRef): Flow<ResultPrimaryAction> {
        if ((ref.key as? PuzzleKey.Daily)?.puzzleNumber == null) return flowOf(ResultPrimaryAction.Done)
        return combine(earlier.of(ref), completions.observeRecords(), calendar.observeToday(slot.gameId)) { same, records, today ->
            val available = daily.archiveRange(slot.gameId)
            val solved = records.mapNotNullTo(mutableSetOf(slot.day)) { record ->
                (record.puzzleRef.key as? PuzzleKey.Daily)?.date?.takeIf { record.puzzleRef.gameId == slot.gameId }
            }
            val day: LocalDate? = if (same.previous != null || available == null) null else nextArchiveDay(solved, available, slot.day, today.minusDays(1))
            day?.let { ResultPrimaryAction.NextDay(slot.gameId, it, gameName) } ?: ResultPrimaryAction.Done
        }
    }
}
