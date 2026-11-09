package com.ivandsky.puzzlebox.games.tangoui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Settle debounce, diff-based (user QA 2026-11-02): ONLY violations newly introduced by the last
 * board change are suppressed for [MotionTokens.SettleMillis]. Violations that already existed
 * stay on screen until the grid change actually fixes them.
 */
internal class TangoSettle(private val scope: CoroutineScope) {
    var suppressed by mutableStateOf<Set<Set<Int>>>(emptySet())
        private set
    private var job: Job? = null

    /** Suppresses [sets] (the violations a change newly introduced) until the window settles. */
    fun suppress(sets: Set<Set<Int>>) {
        suppressed = sets
    }

    /** (Re)starts the window after which new violations show. */
    fun arm() {
        job?.cancel()
        job = scope.launch {
            delay(MotionTokens.SettleMillis)
            suppressed = emptySet()
        }
    }
}
