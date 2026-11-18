package com.ivandsky.puzzlebox.profile.whatsnew

import androidx.lifecycle.ViewModel
import com.ivandsky.puzzlebox.common.log.FailureLog
import com.ivandsky.puzzlebox.common.log.LogcatFailureLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val TAG = "WhatsNew"

/**
 * Reads the bundled notes once, when the page opens. The file is a few KB, so there is no loading state to show. Any
 * failure of [source] (a missing file, a bad line, a bad date) shows the load-failed state and goes to [failures].
 */
internal class WhatsNewViewModel(
    source: WhatsNewSource,
    failures: FailureLog = LogcatFailureLog,
) : ViewModel() {

    private val state = MutableStateFlow(load(source, failures))
    val uiState: StateFlow<WhatsNewUiState> = state.asStateFlow()

    private fun load(source: WhatsNewSource, failures: FailureLog): WhatsNewUiState = try {
        WhatsNewUiState(source.releases().map { WhatsNewCard(it.version, formatReleaseDate(it.date), it.notes) })
    } catch (e: Exception) {
        failures.warn(TAG, "what's new did not load: ${e.message}", e)
        WhatsNewUiState(loadFailed = true)
    }
}
