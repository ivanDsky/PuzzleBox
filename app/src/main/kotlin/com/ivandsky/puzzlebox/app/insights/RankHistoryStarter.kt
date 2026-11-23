package com.ivandsky.puzzlebox.app.insights

import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Starts the weekly rank history filler for the life of the app scope: the history is what the stats page and the year
 * recap show. Starting twice launches once.
 *
 * @param fill the filler's follow-the-account loop; it suspends until the scope is cancelled.
 */
class RankHistoryStarter(
    private val fill: suspend () -> Unit,
    private val scope: CoroutineScope,
) {
    private val started = AtomicBoolean(false)

    fun start() {
        if (!started.compareAndSet(false, true)) return
        scope.launch { fill() }
    }
}
