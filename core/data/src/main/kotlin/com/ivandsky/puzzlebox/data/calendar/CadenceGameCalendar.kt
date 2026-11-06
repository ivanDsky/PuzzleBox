package com.ivandsky.puzzlebox.data.calendar

import com.ivandsky.puzzlebox.game.Cadence
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.model.GameId
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow

/**
 * [GameCalendar] read from each game's [Cadence] in [registry]. [nowMs] is the wall clock
 * (epoch millis); tests drive it from the coroutine scheduler so the midnight wait in
 * [observeToday] runs on virtual time.
 */
internal class CadenceGameCalendar(
    private val registry: GameRegistry,
    private val nowMs: () -> Long = System::currentTimeMillis,
) : GameCalendar {

    override fun today(game: GameId): LocalDate? = dayAt(game, nowMs())

    override fun dayAt(game: GameId, epochMs: Long): LocalDate? = zoneOf(game)?.let { dayIn(it, epochMs) }

    override fun observeToday(game: GameId): Flow<LocalDate> {
        val zone = zoneOf(game) ?: return emptyFlow()
        return flow {
            while (true) {
                val day = dayIn(zone, nowMs())
                emit(day)
                val nextMidnightMs = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
                delay((nextMidnightMs - nowMs()).coerceAtLeast(1L))
            }
        }.distinctUntilChanged()
    }

    private fun zoneOf(game: GameId): ZoneId? =
        when (val cadence = registry[game]?.definition?.cadence) {
            is Cadence.Daily -> cadence.zone
            null -> null
        }

    private fun dayIn(zone: ZoneId, epochMs: Long): LocalDate = Instant.ofEpochMilli(epochMs).atZone(zone).toLocalDate()
}
