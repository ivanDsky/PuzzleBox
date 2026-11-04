package com.ivandsky.puzzlebox.domain

import com.ivandsky.puzzlebox.data.calendar.GameCalendar
import com.ivandsky.puzzlebox.data.repository.CompletionRepository
import com.ivandsky.puzzlebox.data.repository.SavedPlayRepository
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.model.GameId
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * Combines saved plays, completion records and each game's calendar day into one
 * [PlayProgress] (A12, A13, SC10, SC11). The order is [GameRegistry.all] (every registered
 * game is playable), so a new game needs no edit here. A save or record counts only when
 * it is for the game's current day; a calendar rollover re-emits.
 */
class ObservePlayProgress(
    private val registry: GameRegistry,
    private val saved: SavedPlayRepository,
    private val completions: CompletionRepository,
    private val calendar: GameCalendar,
) {
    operator fun invoke(): Flow<PlayProgress> {
        val order = registry.all.map { it.definition.id }
        return combine(todays(order), saved.observeInProgress(), completions.observeRecords()) { today, slots, records ->
            val solved = records
                .filter { today[it.puzzleRef.gameId] == it.puzzleRef.date }
                .mapTo(mutableSetOf()) { it.puzzleRef.gameId }
            val inProgress = slots
                .filter { today[it.gameId] == it.day && it.gameId !in solved }
                .mapTo(mutableSetOf()) { it.gameId }
            PlayProgress(order, inProgress, solved)
        }.distinctUntilChanged()
    }

    private fun todays(order: List<GameId>): Flow<Map<GameId, LocalDate>> =
        if (order.isEmpty()) {
            flowOf(emptyMap())
        } else {
            combine(order.map { id -> calendar.observeToday(id).map { id to it } }) { it.toMap() }
        }
}
