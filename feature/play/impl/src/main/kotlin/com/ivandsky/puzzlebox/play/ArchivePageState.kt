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

/**
 * The selected game's calendar: the shown [month] and its archive [days], the [picked] past day the day card
 * shows (null = today) and the [saved] days of the game that have a saved LinkedIn board (a dot on their disc).
 */
internal data class ArchivePageState(
    val game: GameId,
    val month: YearMonth,
    val days: List<ArchiveDay>,
    val picked: ArchiveDay?,
    val saved: Set<LocalDate>,
)

/**
 * [game]'s page for [shown] (null = today's month) and the past [day] picked for the day card, null while its today is not
 * known. [saved] is the game's saved-board days from one observation, whatever the month or the day.
 */
internal fun ObserveArchiveMonth.page(
    game: GameId,
    shown: YearMonth?,
    day: LocalDate?,
    calendar: GameCalendar,
    saved: Flow<Set<LocalDate>>,
): Flow<ArchivePageState?> {
    val target = shown ?: calendar.today(game)?.let(YearMonth::from) ?: return flowOf(null)
    val days = this(game, target)
    if (day == null) return combine(days, saved) { list, boards -> ArchivePageState(game, target, list, null, boards) }
    val dayMonth = YearMonth.from(day)
    if (dayMonth == target) {
        return combine(days, saved) { list, boards ->
            ArchivePageState(game, target, list, list.firstOrNull { it.date == day }, boards)
        }
    }
    // The picked day sits in another month: its own query keeps its status live.
    return combine(days, this(game, dayMonth), saved) { list, other, boards ->
        ArchivePageState(game, target, list, other.firstOrNull { it.date == day }, boards)
    }
}
