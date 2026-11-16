package com.ivandsky.puzzlebox.gameui

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** The source that always holds [PlayTuning.Starting]; ticket 57 replaces the value with the chosen one. */
object ShippedPlayTuningSource : PlayTuningSource {
    override val tuning: StateFlow<PlayTuning?> = MutableStateFlow(PlayTuning.Starting)
}
