package com.ivandsky.puzzlebox.play.linkedin

import com.ivandsky.puzzlebox.data.calendar.GameCalendar
import com.ivandsky.puzzlebox.domain.linkedin.LinkedInDayView
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.play.LibraryUiState
import com.ivandsky.puzzlebox.play.linkedin.LinkedInDayState.Target
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf

/**
 * The day card's LinkedIn view for the library's (selected game, picked day). With nothing picked it
 * follows the day the card shows: the selected row's `cardDay` (its latest published day, which is yesterday
 * while today's puzzle is not out), else the calendar's today. A null receiver is the feature off: it shows nothing and reaches nothing. A game
 * whose today is not known yet has no target, so nothing is observed or synced for it.
 */
internal fun LinkedInDayState?.libraryView(
    game: Flow<GameId>,
    picked: Flow<LocalDate?>,
    library: Flow<LibraryUiState>,
    calendar: GameCalendar,
): Flow<LinkedInDayView?> {
    val holder = this ?: return flowOf(null)
    return holder.view(
        combine(game, picked, cardDay(game, library)) { selected, day, latest ->
            (day ?: latest ?: calendar.today(selected))?.let { Target(selected, it) }
        },
    )
}

private fun cardDay(game: Flow<GameId>, library: Flow<LibraryUiState>): Flow<LocalDate?> =
    combine(game, library) { selected, state -> state.rows.firstOrNull { it.card.game == selected }?.cardDay }
        .distinctUntilChanged()
