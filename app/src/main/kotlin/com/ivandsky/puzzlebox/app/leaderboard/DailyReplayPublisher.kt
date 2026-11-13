package com.ivandsky.puzzlebox.app.leaderboard

import com.ivandsky.puzzlebox.domain.ranks.SubmitDailyReplay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Keeps [SubmitDailyReplay] running for the life of the app scope, beside the submit watcher whose accepted posts it
 * follows. The application starts it only with the hub flag on.
 */
class DailyReplayPublisher(
    private val publish: SubmitDailyReplay,
    private val scope: CoroutineScope,
) {
    fun start() {
        scope.launch { publish() }
    }
}
