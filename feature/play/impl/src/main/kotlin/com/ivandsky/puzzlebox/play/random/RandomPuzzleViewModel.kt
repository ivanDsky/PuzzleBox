package com.ivandsky.puzzlebox.play.random

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivandsky.puzzlebox.common.dispatcher.DispatcherProvider
import com.ivandsky.puzzlebox.domain.random.LoadRandomPuzzle
import com.ivandsky.puzzlebox.domain.random.RandomFilter
import com.ivandsky.puzzlebox.domain.random.RandomOutcome
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.play.community.DifficultyChoice
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The random sheet's filters and its one pick at a time. [game] is the preset (null: Any game), applied once when the
 * screen first creates this; the screen keys it per game, so the filters stay as the player left them while it lives.
 * A pick is remembered in [memory] only once it is chosen, so a pick [reset] cancels never touches it.
 */
internal class RandomPuzzleViewModel(
    game: GameId?,
    private val load: LoadRandomPuzzle,
    private val memory: RandomPickMemory,
    private val dispatchers: DispatcherProvider,
) : ViewModel() {
    private val current = MutableStateFlow(
        RandomPuzzleState(RandomSource.Past, game, unsolvedOnly = true, difficulty = null, phase = RandomPhase.Idle),
    )
    private var running: Job? = null

    val state: StateFlow<RandomPuzzleState> = current.asStateFlow()

    fun chooseSource(source: RandomSource) = change { it.copy(source = source) }

    fun chooseGame(game: GameId?) = change { it.copy(game = game) }

    fun setUnsolvedOnly(on: Boolean) = change { it.copy(unsolvedOnly = on) }

    fun chooseDifficulty(choice: DifficultyChoice?) = change { it.copy(difficulty = choice) }

    /** Picks one puzzle by the filters as they are now; ignored while a pick runs or its puzzle is open. */
    fun pick() {
        val start = current.value
        if (start.phase is RandomPhase.Picking || start.phase is RandomPhase.Opened) return
        current.value = start.copy(phase = RandomPhase.Picking)
        val filter = when (start.source) {
            RandomSource.Past -> RandomFilter.Archive(start.game, start.unsolvedOnly)
            RandomSource.Community -> RandomFilter.Community(start.game, start.difficulty?.levels.orEmpty())
        }
        running = viewModelScope.launch {
            val outcome = withContext(dispatchers.default) { load(filter, memory.last) }
            if (outcome is RandomOutcome.Picked) memory.last = outcome.pick
            current.update { it.copy(phase = phaseOf(outcome, start.source)) }
        }
    }

    /** The sheet closed or its puzzle was handed on: drop a pick in flight and go back to Idle. The filters stay. */
    fun reset() {
        running?.cancel()
        current.update { it.copy(phase = RandomPhase.Idle) }
    }

    /** A filter changed: it clears a message, and is ignored while a pick runs or its puzzle is open. */
    private fun change(edit: (RandomPuzzleState) -> RandomPuzzleState) = current.update {
        if (it.phase is RandomPhase.Picking || it.phase is RandomPhase.Opened) it else edit(it).copy(phase = RandomPhase.Idle)
    }

    private fun phaseOf(outcome: RandomOutcome, source: RandomSource): RandomPhase = when (outcome) {
        is RandomOutcome.Picked -> RandomPhase.Opened(outcome.pick)
        RandomOutcome.NothingLeft -> RandomPhase.Said(
            if (source == RandomSource.Past) RandomMessage.NothingLeftPast else RandomMessage.NothingLeftCommunity,
        )
        RandomOutcome.NoneOpened -> RandomPhase.Said(RandomMessage.NoneOpened)
        RandomOutcome.Offline -> RandomPhase.Said(RandomMessage.Offline)
        RandomOutcome.Throttled -> RandomPhase.Said(RandomMessage.Throttled)
        RandomOutcome.Refused -> RandomPhase.Said(RandomMessage.Refused)
    }
}
