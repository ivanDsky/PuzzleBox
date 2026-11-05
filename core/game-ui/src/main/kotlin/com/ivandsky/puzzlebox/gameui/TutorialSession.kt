package com.ivandsky.puzzlebox.gameui

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * The tutorial's step machine (spec A11), free of Compose and Android so it is plain to
 * test and can live in a ViewModel across rotation. It starts on the first lesson; a win
 * [advance]s it to the same lesson, solved, and [next] then moves to the next lesson, or
 * to [TutorialState.Ready] after the last. [skip] ends it early. Both end states are final.
 */
class TutorialSession(private val steps: List<TutorialStep>) {

    init {
        require(steps.isNotEmpty()) { "a tutorial needs at least one step" }
    }

    private val _state = MutableStateFlow<TutorialState>(playing(0))
    val state: StateFlow<TutorialState> = _state.asStateFlow()

    /** The current lesson is won: it stays on screen, solved. A solved lesson stays as it is. */
    fun advance() {
        _state.update { current ->
            if (current is TutorialState.Playing && !current.solved) current.copy(solved = true) else current
        }
    }

    /** After a solved lesson: on to the next one, or [TutorialState.Ready] after the last. */
    fun next() {
        _state.update { current ->
            if (current !is TutorialState.Playing || !current.solved) return@update current
            val next = current.index + 1
            if (next < steps.size) playing(next) else TutorialState.Ready
        }
    }

    /** The player skips the rest. A finished tutorial stays finished. */
    fun skip() {
        _state.update { current -> if (current is TutorialState.Playing) TutorialState.Skipped else current }
    }

    private fun playing(index: Int) = TutorialState.Playing(index, steps.size, steps[index])
}
