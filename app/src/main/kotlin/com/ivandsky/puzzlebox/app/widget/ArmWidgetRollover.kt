package com.ivandsky.puzzlebox.app.widget

import android.util.Log
import java.time.Clock
import java.time.ZoneId

/**
 * Sets the one pending rollover wake-up to the next midnight across the listed games' [zones], replacing the last. The
 * wake-up exists only while a widget is placed: without one ([hasWidgets] false) it cancels instead, which also clears a
 * leftover. No zones schedules nothing. The widget is a convenience, so a failure is one log line, never a crash of the caller.
 */
class ArmWidgetRollover(
    private val zones: () -> Collection<ZoneId>,
    private val hasWidgets: () -> Boolean,
    private val alarm: WidgetRolloverAlarm,
    private val clock: Clock,
) {
    private val nextRollover = ComputeWidgetRollover()

    operator fun invoke() {
        try {
            if (!hasWidgets()) {
                alarm.cancel()
                return
            }
            val zones = zones()
            if (zones.isEmpty()) return
            alarm.schedule(nextRollover(clock.instant(), zones))
        } catch (e: Exception) {
            Log.w(TAG, "widget rollover not armed: ${e.message}")
        }
    }

    private companion object {
        const val TAG = "ArmWidgetRollover"
    }
}
