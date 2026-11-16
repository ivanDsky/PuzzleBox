package com.ivandsky.puzzlebox.gameui

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** The source that always holds [PlayTuning.Shipped]. The release flag decides whether play sees it. */
object ShippedPlayTuningSource : PlayTuningSource {
    override val tuning: StateFlow<PlayTuning?> = MutableStateFlow(PlayTuning.Shipped)
}
