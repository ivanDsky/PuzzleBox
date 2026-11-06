package com.ivandsky.puzzlebox.play

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivandsky.puzzlebox.data.repository.DailyRepository
import com.ivandsky.puzzlebox.domain.ObserveTodaySet
import com.ivandsky.puzzlebox.domain.TodayStatus
import com.ivandsky.puzzlebox.model.GameId
import java.time.LocalDate
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * One hub row: a playable game, its display name, today's status (null = not yet known)
 * and the days it can open by date (null = no archive, so no "Past puzzles").
 */
internal data class HubRow(
    val game: GameId,
    val name: String,
    val status: TodayStatus? = null,
    val archive: ClosedRange<LocalDate>? = null,
)

/**
 * The games hub's state holder: the [PlaySessionStore]'s playable games in registry order,
 * each with its [ObserveTodaySet] status (A13, A14, SC11) and its
 * [DailyRepository.archiveRange] (A22). Before the first emission no row has a status. The
 * set is collected only while the hub is observed (plus a 5 s grace), so a hub left in a
 * back stack across a deep sleep re-reads the day's cues — and the range's end — when it
 * shows again. Nothing here reads or fetches a daily: the range is the source's arithmetic.
 */
class HubViewModel(
    store: PlaySessionStore,
    todaySet: ObserveTodaySet,
    private val daily: DailyRepository,
) : ViewModel() {

    private val playable = store.playable

    internal val rows: StateFlow<List<HubRow>> = todaySet()
        .map { entries ->
            val status = entries.associate { it.game to it.status }
            playable.map { (game, name) -> HubRow(game, name, status[game], daily.archiveRange(game)) }
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            playable.map { (game, name) -> HubRow(game, name, archive = daily.archiveRange(game)) },
        )

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
