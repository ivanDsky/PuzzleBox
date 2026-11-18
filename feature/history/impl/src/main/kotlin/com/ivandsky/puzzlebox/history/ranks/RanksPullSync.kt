package com.ivandsky.puzzlebox.history.ranks

import com.ivandsky.puzzlebox.domain.linkedin.ObserveLinkedInDayView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * A pull on the hub's board asks LinkedIn for the day the board shows ([shown], null when the board has no LinkedIn part),
 * once at a time: a pull while one is running adds nothing. A sync sends a call only when the day's read window allows one
 * (see [ObserveLinkedInDayView.refresh]), so a pull never spends more than the day card's own sync would.
 * [syncing] holds while it runs, so the pull indicator stays for as long as LinkedIn is being asked.
 */
internal class RanksPullSync(
    private val linkedIn: ObserveLinkedInDayView,
    private val scope: CoroutineScope,
    private val shown: suspend () -> ObserveLinkedInDayView.Target?,
) {
    private val _syncing = MutableStateFlow(false)
    val syncing: StateFlow<Boolean> = _syncing.asStateFlow()
    private var running: Job? = null

    fun pull() {
        if (running?.isActive == true) return
        running = scope.launch {
            val target = shown() ?: return@launch
            _syncing.value = true
            try {
                linkedIn.refresh(target)
            } finally {
                _syncing.value = false
            }
        }
    }
}
