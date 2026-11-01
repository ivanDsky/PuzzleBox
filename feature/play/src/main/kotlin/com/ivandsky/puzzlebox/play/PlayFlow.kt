package com.ivandsky.puzzlebox.play

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.ivandsky.puzzlebox.data.repository.CompletionRepository
import com.ivandsky.puzzlebox.data.repository.ZipDailyRepository
import com.ivandsky.puzzlebox.games.zip.ZipSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * Owner of the play flow's [ZipPlayState] (ticket 23). The state must outlive the play
 * entry's composition once the result route replaces it: the record write is
 * fire-and-observe (a navigation must not cancel it), the badge verdict lands on the
 * result screen, and "Play again" resets the same puzzle in place. A Koin single is the
 * process lifetime; [end] releases the scope on a mid-game exit.
 *
 * The session-changed callback serializes into the play entry's `rememberSaveable` —
 * that closure belongs to the entry that STARTED the flow; a re-entered entry keeps the
 * already-live session instead of re-registering (the bytes only matter for process
 * death while that entry is on screen).
 */
class PlayFlow(
    private val daily: ZipDailyRepository,
    private val completions: CompletionRepository,
) {
    private var flowScope: CoroutineScope? = null

    // Snapshot-backed: the result route composes BEFORE the flow may have started
    // (fresh entry) and must recompose when the state appears — a plain var would
    // strand the route on a stale null.
    var state: ZipPlayState? by mutableStateOf(null)
        private set

    /** The flow's state, created on first entry and reused until [end]. */
    fun start(onSessionChanged: (ZipSession?) -> Unit): ZipPlayState {
        state?.let { return it }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        flowScope = scope
        return ZipPlayState(
            daily = daily,
            completions = completions,
            scope = scope,
            onSessionChanged = onSessionChanged,
        ).also { state = it }
    }

    /** Mid-game exit: the next play entry must fetch fresh, never resume. */
    fun end() {
        flowScope?.cancel()
        flowScope = null
        state = null
    }
}
