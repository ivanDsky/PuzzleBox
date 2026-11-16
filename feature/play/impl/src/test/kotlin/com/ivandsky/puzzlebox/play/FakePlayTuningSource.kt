package com.ivandsky.puzzlebox.play

import com.ivandsky.puzzlebox.gameui.PlayTuning
import com.ivandsky.puzzlebox.gameui.PlayTuningSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** A [PlayTuningSource] a test holds: [PlayTuning.Starting] by default, null for the legacy layout; call [set] to change it live. */
internal class FakePlayTuningSource(initial: PlayTuning? = PlayTuning.Starting) : PlayTuningSource {
    private val state = MutableStateFlow(initial)

    override val tuning: StateFlow<PlayTuning?> get() = state

    fun set(value: PlayTuning?) {
        state.value = value
    }
}
