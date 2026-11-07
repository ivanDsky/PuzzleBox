package com.ivandsky.puzzlebox.play.friend

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivandsky.puzzlebox.common.dispatcher.DispatcherProvider
import com.ivandsky.puzzlebox.data.model.ReceivedPuzzle
import com.ivandsky.puzzlebox.data.repository.CompletionRepository
import com.ivandsky.puzzlebox.domain.LoadSharedPuzzle
import com.ivandsky.puzzlebox.domain.OpenOutcome
import com.ivandsky.puzzlebox.model.ShareCode
import com.ivandsky.puzzlebox.play.api.CustomGameKey
import com.ivandsky.puzzlebox.play.api.FriendPuzzleKey
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What the friend's puzzle card draws. */
internal sealed interface FriendCardState {
    data object Loading : FriendCardState

    /** The opened [puzzle]; [solved] once any completion of it is recorded here. */
    data class Ready(val puzzle: ReceivedPuzzle, val solved: Boolean) : FriendCardState {
        /** The board this card's Play opens. */
        val play: CustomGameKey get() = CustomGameKey(puzzle.gameId.value, puzzle.code.value)
    }

    /** The code did not open; [outcome] says why. */
    data class Failed(val outcome: OpenOutcome) : FriendCardState
}

/**
 * One [FriendPuzzleKey]'s card: opens the code once with [LoadSharedPuzzle] (a puzzle already
 * on this device opens without the network), then follows the puzzle's completions so the
 * card reads Solved as soon as a solve is recorded.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class FriendPuzzleViewModel(
    key: FriendPuzzleKey,
    load: LoadSharedPuzzle,
    completions: CompletionRepository,
    dispatchers: DispatcherProvider,
) : ViewModel() {

    private val opened = MutableStateFlow<OpenOutcome?>(null)

    val state: StateFlow<FriendCardState> = opened
        .flatMapLatest { outcome ->
            when (outcome) {
                null -> flowOf(FriendCardState.Loading)
                is OpenOutcome.Opened -> completions.observeCustom(outcome.puzzle.gameId, outcome.puzzle.code.value)
                    .map { records -> FriendCardState.Ready(outcome.puzzle, solved = records.isNotEmpty()) }
                else -> flowOf(FriendCardState.Failed(outcome))
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), FriendCardState.Loading)

    init {
        viewModelScope.launch {
            val code = ShareCode.parse(key.code)
            opened.value = if (code == null) OpenOutcome.NotFound else withContext(dispatchers.default) { load(code) }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
