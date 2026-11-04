package com.ivandsky.puzzlebox.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.data.identity.IdentityService
import com.ivandsky.puzzlebox.domain.GameSummary
import com.ivandsky.puzzlebox.domain.GetFeaturedGame
import com.ivandsky.puzzlebox.domain.ObserveGameHistory
import com.ivandsky.puzzlebox.game.GameRegistry
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** Everything Home renders: the featured game, the signed-in player, the history teaser. */
data class HomeUiState(
    val featured: GameSummary,
    val identity: Identity?,
    val history: List<HistoryDay>,
)

/**
 * Home's state holder: observes the GAME-AGNOSTIC merged history ([ObserveGameHistory] —
 * every game's completion records UNION every registered game's captured days; fix
 * round 5, defect C). Per-game names and the featured game resolve against :app's
 * [GameRegistry], so a new game appears here purely by registering its plugin. The
 * initial state is the first frame Home always showed: an empty teaser and the current
 * identity.
 */
class HomeViewModel(
    observeGameHistory: ObserveGameHistory,
    getFeaturedGame: GetFeaturedGame,
    identityService: IdentityService,
    private val registry: GameRegistry,
) : ViewModel() {

    private val featured = getFeaturedGame()

    val uiState: StateFlow<HomeUiState> =
        combine(observeGameHistory(), identityService.current) { days, identity ->
            HomeUiState(
                featured = featured,
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
            initialValue = HomeUiState(featured, identityService.current.value, emptyList()),
        )

    private companion object {
        val HEADLINE: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM d")
    }
}
