package com.ivandsky.puzzlebox.app.widget

import java.time.Instant

/** Records every scheduled instant and every cancel; [failure] makes [schedule] throw like an alarm service that refuses. */
class FakeWidgetRolloverAlarm(private val failure: Exception? = null) : WidgetRolloverAlarm {
    val scheduled = mutableListOf<Instant>()
    var cancels = 0
        private set

    override fun schedule(at: Instant) {
        failure?.let { throw it }
        scheduled += at
    }

    override fun cancel() {
        cancels++
    }
}
