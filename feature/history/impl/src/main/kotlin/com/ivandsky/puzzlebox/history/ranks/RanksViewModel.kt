package com.ivandsky.puzzlebox.history.ranks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivandsky.puzzlebox.domain.ObserveLeaderboard
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.gameui.board.DailyBoardState
import com.ivandsky.puzzlebox.gameui.board.dailyBoardTitle
import com.ivandsky.puzzlebox.gameui.board.toDailyBoardState
import com.ivandsky.puzzlebox.model.GameId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

/**
 * The Ranks hub's state holder: one [selection] (what the player picked) and the [uiState]
 * derived from it, slice by slice.
 *
 * The board slice is the first listed game's board for its calendar today, through the shipped
 * kit, whichever board is selected: Overall's math and the picked day arrive with later tickets,
 * which replace this source and nothing around it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class RanksViewModel(
    private val leaderboard: ObserveLeaderboard,
    private val registry: GameRegistry,
) : ViewModel() {

    private val _selection = MutableStateFlow(RanksSelection())
    val selection: StateFlow<RanksSelection> = _selection.asStateFlow()

    private val firstGame: GameId? = rankBoards(registry).filterIsInstance<RanksBoard.Game>().firstOrNull()?.id

    val uiState: StateFlow<RanksUiState> = selection
        .map { (it.board as? RanksBoard.Game)?.id ?: firstGame }
        .distinctUntilChanged()
        .flatMapLatest { game -> boardSlice(game) }
        .map(::RanksUiState)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), RanksUiState(slice(firstGame, DailyBoardState.Loading)))

    /** Applies one of [RanksSelection]'s transitions; the policy stays in the value. */
    fun select(change: (RanksSelection) -> RanksSelection) {
        _selection.update(change)
    }

    private fun boardSlice(game: GameId?): Flow<RanksBoardSlice> =
        if (game == null) {
            flowOf(slice(null, DailyBoardState.Unavailable))
        } else {
            leaderboard.today(game).map { slice(game, it.toDailyBoardState()) }
        }

    private fun slice(game: GameId?, state: DailyBoardState) = RanksBoardSlice(
        title = dailyBoardTitle(game?.let(registry::displayName)),
        order = game?.let(registry::rankOrderOf) ?: RankOrder.TimeThenHints,
        state = state,
    )

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
