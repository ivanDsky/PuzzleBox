package com.ivandsky.puzzlebox.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.data.identity.IdentityService
import com.ivandsky.puzzlebox.domain.ObserveGameHistory
import com.ivandsky.puzzlebox.domain.ObserveTodaySet
import com.ivandsky.puzzlebox.domain.TodayStatus
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.model.GameId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn

/** Everything Home renders: today's set, the signed-in player, the history teaser. */
data class HomeUiState(
    val today: List<TodayRow>,
    val identity: Identity?,
    val history: List<HistoryDay>,
) {
    /** One game in the Today card (A15); [status] is null until today's set first emits. */
    data class TodayRow(val game: GameId, val displayName: String, val status: TodayStatus?)
}

/**
 * Home's state holder. The Today card is [ObserveTodaySet] (A15): every registered game in
 * registry order with its status, seeded from [registry] so the first frame already lists
 * every game. The teaser observes the GAME-AGNOSTIC merged history ([ObserveGameHistory] —
 * every game's completion records UNION every registered game's captured days; fix
 * round 5, defect C); per-game names resolve against :app's [GameRegistry], so a new game
 * appears here purely by registering its plugin.
 */
class HomeViewModel(
    observeGameHistory: ObserveGameHistory,
    todaySet: ObserveTodaySet,
    identityService: IdentityService,
    private val registry: GameRegistry,
) : ViewModel() {

    private val seed = registry.all.map {
        HomeUiState.TodayRow(it.definition.id, it.definition.displayName, status = null)
    }

    // Seeded so the teaser never waits on today's set (it reads Room and DataStore).
    private val today = todaySet()
        .map { entries -> entries.map { HomeUiState.TodayRow(it.game, it.displayName, it.status) } }
        .onStart { emit(seed) }

    val uiState: StateFlow<HomeUiState> =
        combine(today, observeGameHistory(), identityService.current) { rows, days, identity ->
            HomeUiState(
                today = rows,
                identity = identity,
                history = days.map { day ->
                    HistoryDay(
                        isoDay = day.day.toString(),
                        headline = day.day.format(HEADLINE),
                        gameName = registry.displayName(day.gameId),
                    )
                },
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = HomeUiState(seed, identityService.current.value, emptyList()),
        )

    private companion object {
        val HEADLINE: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM d")
    }
}
