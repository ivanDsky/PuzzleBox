package com.ivandsky.puzzlebox.play

import com.ivandsky.puzzlebox.data.calendar.GameCalendar
import com.ivandsky.puzzlebox.domain.ArchiveDay
import com.ivandsky.puzzlebox.domain.ObserveArchiveMonth
import com.ivandsky.puzzlebox.model.GameId
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * The selected game's calendar: the shown [month] and its archive [days], and the
 * [picked] past day the day card shows (null = today).
 */
internal data class ArchivePageState(
    val game: GameId,
    val month: YearMonth,
    val days: List<ArchiveDay>,
    val picked: ArchiveDay?,
)

/** [game]'s page for [shown] (null = today's month) and the past [day] picked for the day card, null while its today is not known. */
internal fun ObserveArchiveMonth.page(
    game: GameId,
    shown: YearMonth?,
    day: LocalDate?,
    calendar: GameCalendar,
): Flow<ArchivePageState?> {
    val target = shown ?: calendar.today(game)?.let(YearMonth::from) ?: return flowOf(null)
    val days = this(game, target)
    if (day == null) return days.map { ArchivePageState(game, target, it, null) }
    val dayMonth = YearMonth.from(day)
    if (dayMonth == target) {
        return days.map { list -> ArchivePageState(game, target, list, list.firstOrNull { it.date == day }) }
    }
    // The picked day sits in another month: its own query keeps its status live.
    return combine(days, this(game, dayMonth)) { list, other ->
        ArchivePageState(game, target, list, other.firstOrNull { it.date == day })
    }
}
