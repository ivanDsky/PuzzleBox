package com.ivandsky.puzzlebox.play

import com.ivandsky.puzzlebox.data.calendar.GameCalendar
import com.ivandsky.puzzlebox.play.domain.ArchiveDay
import com.ivandsky.puzzlebox.play.domain.ObserveArchiveMonth
import com.ivandsky.puzzlebox.model.GameId
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * The selected game's calendar: the shown [month] and its archive [days], and the
 * [picked] past day the day card shows (null = today). A swiping calendar (backlog #1668) also has the days of the months
 * either side in [around], so the next month is drawn, whole, as the grid is dragged toward it.
 */
internal data class ArchivePageState(
    val game: GameId,
    val month: YearMonth,
    val days: List<ArchiveDay>,
    val picked: ArchiveDay?,
    val around: Map<YearMonth, List<ArchiveDay>> = emptyMap(),
)

/**
 * [game]'s page for [shown] (null = today's month) and the past [day] picked for the day card, null while its today is not known.
 * With [beside] it also loads the months before and after (empty outside the archive).
 */
internal fun ObserveArchiveMonth.page(
    game: GameId,
    shown: YearMonth?,
    day: LocalDate?,
    calendar: GameCalendar,
    beside: Boolean = false,
): Flow<ArchivePageState?> {
    val target = shown ?: calendar.today(game)?.let(YearMonth::from) ?: return flowOf(null)
    val page = pageOf(game, target, day)
    if (!beside) return page
    val before = target.minusMonths(1)
    val after = target.plusMonths(1)
    return combine(page, this(game, before), this(game, after)) { state, earlier, later ->
        state.copy(around = mapOf(before to earlier, after to later))
    }
}

private fun ObserveArchiveMonth.pageOf(game: GameId, target: YearMonth, day: LocalDate?): Flow<ArchivePageState> {
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
