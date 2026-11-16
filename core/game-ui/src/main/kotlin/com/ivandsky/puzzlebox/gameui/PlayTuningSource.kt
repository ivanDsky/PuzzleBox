package com.ivandsky.puzzlebox.gameui

import kotlinx.coroutines.flow.StateFlow

/** Where play screens get their [PlayTuning]. Null means the legacy layout, today's behaviour. */
interface PlayTuningSource {
    val tuning: StateFlow<PlayTuning?>
}
