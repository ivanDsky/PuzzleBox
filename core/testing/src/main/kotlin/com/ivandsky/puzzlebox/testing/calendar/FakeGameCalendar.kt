package com.ivandsky.puzzlebox.testing.calendar

import com.ivandsky.puzzlebox.data.calendar.GameCalendar
import com.ivandsky.puzzlebox.model.GameId
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * Settable [GameCalendar]: every game reads [initial] until [set] pins it or [setAll]
 * moves every game at once (a simulated midnight). Every game counts as registered and
 * [dayAt] reads instants in [zone].
 */
class FakeGameCalendar(initial: LocalDate, private val zone: ZoneId = ZoneOffset.UTC) : GameCalendar {

    private val state = MutableStateFlow(Days(initial, emptyMap()))

    override fun today(game: GameId): LocalDate = state.value.of(game)

    override fun dayAt(game: GameId, epochMs: Long): LocalDate = Instant.ofEpochMilli(epochMs).atZone(zone).toLocalDate()

    override fun observeToday(game: GameId): Flow<LocalDate> =
        state.map { it.of(game) }.distinctUntilChanged()

    fun set(game: GameId, day: LocalDate) {
        state.update { it.copy(perGame = it.perGame + (game to day)) }
    }

    fun setAll(day: LocalDate) {
        state.value = Days(day, emptyMap())
    }

    private data class Days(val default: LocalDate, val perGame: Map<GameId, LocalDate>) {
        fun of(game: GameId): LocalDate = perGame[game] ?: default
    }
}
