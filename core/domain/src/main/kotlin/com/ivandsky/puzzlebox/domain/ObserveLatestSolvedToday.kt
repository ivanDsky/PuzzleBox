package com.ivandsky.puzzlebox.domain

import com.ivandsky.puzzlebox.data.calendar.GameCalendar
import com.ivandsky.puzzlebox.data.repository.CompletionRepository
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.model.GameId
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf

/**
 * The game whose official daily the player solved most recently on that game's today (A10);
 * null if none today. A solve counts when [isOfficialDaily] holds for the game's own
 * [GameCalendar] day, so practice, custom and past-day records never win; the latest
 * `firstCompletedAt` wins and a record without one sorts last. Re-evaluates when a record
 * lands or any game's day rolls over.
 */
class ObserveLatestSolvedToday(
    private val completions: CompletionRepository,
    private val calendar: GameCalendar,
    private val registry: GameRegistry,
) {

    operator fun invoke(): Flow<GameId?> {
        val games = registry.all.map { it.definition.id }
        if (games.isEmpty()) return flowOf(null)
        val days: Flow<Map<GameId, LocalDate>> =
            combine(games.map { game -> calendar.observeToday(game) }) { today -> games.zip(today.toList()).toMap() }
        return combine(completions.observeRecords(), days) { records, today ->
            records
                .filter { record ->
                    val day = today[record.puzzleRef.gameId]
                    day != null && record.puzzleRef.key.isOfficialDaily(on = day)
                }
                .maxWithOrNull(compareBy(nullsFirst<Instant>()) { it.firstCompletedAt })
                ?.puzzleRef?.gameId
        }.distinctUntilChanged()
    }
}
