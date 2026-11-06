package com.ivandsky.puzzlebox.domain

import com.ivandsky.puzzlebox.data.calendar.GameCalendar
import com.ivandsky.puzzlebox.data.repository.CompletionRepository
import com.ivandsky.puzzlebox.data.repository.DailyRepository
import com.ivandsky.puzzlebox.data.repository.SavedPlayRepository
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.PlaySlot
import com.ivandsky.puzzlebox.model.PuzzleKey
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * Today's set (spec A14, A12/A13): every registered game in [GameRegistry.all] order, with
 * its status on its own calendar day ([GameCalendar.observeToday], never the device date;
 * a rollover re-emits). Solved = a daily completion record (a custom one never counts, A12)
 * with `ref.gameId == game && ref.date == today` (the identity [ObserveGameHistory] uses)
 * and beats a saved play. InProgress =
 * a saved play in the (game, today) slot, at its elapsed time when last saved. Offline =
 * neither, today's puzzle is not in the daily cache and [sync]'s last fetch for the game
 * failed (A12); a later capture clears it. Anything else, another day's saved play
 * included, is NotStarted.
 */
class ObserveTodaySet(
    private val registry: GameRegistry,
    private val saved: SavedPlayRepository,
    private val completions: CompletionRepository,
    private val calendar: GameCalendar,
    private val daily: DailyRepository,
    private val sync: SyncTodayPuzzles,
) {

    operator fun invoke(): Flow<List<TodayEntry>> {
        val games = registry.all.map { it.definition }
        val order = games.map { it.id }
        return combine(
            todays(order),
            saved.observeInProgress(),
            completions.observeRecords(),
            capturedDays(order),
            sync.failed,
        ) { today, slots, records, captured, failed ->
            games.map { definition ->
                val day = today.getValue(definition.id)
                val solved = records.firstOrNull {
                    it.puzzleRef.gameId == definition.id &&
                        (it.puzzleRef.key as? PuzzleKey.Daily)?.date == day
                }
                val elapsed = slots[PlaySlot(definition.id, day)]
                val status = when {
                    solved != null -> TodayStatus.Solved(solved.time)
                    elapsed != null -> TodayStatus.InProgress(elapsed)
                    definition.id in failed && day !in captured.getValue(definition.id) -> TodayStatus.Offline
                    else -> TodayStatus.NotStarted
                }
                TodayEntry(definition.id, definition.displayName, status)
            }
        }.distinctUntilChanged()
    }

    private fun todays(order: List<GameId>): Flow<Map<GameId, LocalDate>> =
        if (order.isEmpty()) {
            flowOf(emptyMap())
        } else {
            combine(order.map { id -> calendar.observeToday(id).map { id to it } }) { it.toMap() }
        }

    /** Each game's cached days, so a puzzle captured after a failed sync no longer reads Offline. */
    private fun capturedDays(order: List<GameId>): Flow<Map<GameId, Set<LocalDate>>> =
        if (order.isEmpty()) {
            flowOf(emptyMap())
        } else {
            val perGame = order.map { id ->
                daily.observeHistory(id).map { entries -> id to entries.mapTo(HashSet()) { it.day } }
            }
            combine(perGame) { it.toMap() }
        }
}
