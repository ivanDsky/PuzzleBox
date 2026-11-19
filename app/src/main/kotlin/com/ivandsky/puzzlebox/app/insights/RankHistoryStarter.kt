package com.ivandsky.puzzlebox.app.insights

import com.ivandsky.puzzlebox.common.flags.ReleaseFlags
import com.ivandsky.puzzlebox.common.flags.isEnabled
import com.ivandsky.puzzlebox.insights.flags.InsightsFlag
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Starts the weekly rank history filler for the life of the app scope, but only when a surface that shows the history is
 * on: the stats page or the year recap. The flags are read once at start (ADR-0022): a flag flipped later takes effect
 * on the next launch. With both off nothing is read and no work is launched. Starting twice launches once.
 *
 * @param fill the filler's follow-the-account loop; it suspends until the scope is cancelled.
 */
class RankHistoryStarter(
    private val flags: ReleaseFlags,
    private val fill: suspend () -> Unit,
    private val scope: CoroutineScope,
) {
    private val started = AtomicBoolean(false)

    fun start() {
        if (!(flags.isEnabled(InsightsFlag.StatsPage) || flags.isEnabled(InsightsFlag.YearRecap))) return
        if (!started.compareAndSet(false, true)) return
        scope.launch { fill() }
    }
}
