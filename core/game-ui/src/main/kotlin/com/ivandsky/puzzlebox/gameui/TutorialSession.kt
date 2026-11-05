package com.ivandsky.puzzlebox.gameui

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * The tutorial's step machine (spec A11), free of Compose and Android so it is plain to
 * test and can live in a ViewModel across rotation. It starts on the first lesson; a win
 * [advance]s to the next one, and the last win lands on [TutorialState.Ready]. [skip]
 * ends it early. Both end states are final.
 */
class TutorialSession(private val steps: List<TutorialStep>) {

    init {
        require(steps.isNotEmpty()) { "a tutorial needs at least one step" }
    }

    private val _state = MutableStateFlow<TutorialState>(playing(0))
    val state: StateFlow<TutorialState> = _state.asStateFlow()

    /** The current lesson is solved: on to the next one, or [TutorialState.Ready] after the last. */
    fun advance() {
        _state.update { current ->
            if (current !is TutorialState.Playing) return@update current
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
