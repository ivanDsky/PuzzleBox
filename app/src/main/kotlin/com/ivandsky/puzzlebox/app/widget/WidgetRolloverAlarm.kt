package com.ivandsky.puzzlebox.app.widget

import java.time.Instant

/** The one pending wake-up that redraws the widget at the next game-day rollover; each [schedule] replaces the last. */
interface WidgetRolloverAlarm {
    fun schedule(at: Instant)
    fun cancel()
}
