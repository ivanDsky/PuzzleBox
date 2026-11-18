package com.ivandsky.puzzlebox.profile.whatsnew

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivandsky.puzzlebox.common.dispatcher.DispatcherProvider
import com.ivandsky.puzzlebox.common.log.FailureLog
import com.ivandsky.puzzlebox.common.log.LogcatFailureLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "WhatsNew"

/**
 * Reads the bundled notes once, when the page opens, on [DispatcherProvider.io]: the state is empty (not failed) until
 * the read ends. Any failure of [source] (a missing file, a bad line, a bad date) shows the load-failed state and goes
 * to [failures].
 */
internal class WhatsNewViewModel(
    source: WhatsNewSource,
    dispatchers: DispatcherProvider,
    failures: FailureLog = LogcatFailureLog,
) : ViewModel() {

    private val state = MutableStateFlow(WhatsNewUiState())
    val uiState: StateFlow<WhatsNewUiState> = state.asStateFlow()

    init {
        viewModelScope.launch { state.value = withContext(dispatchers.io) { load(source, failures) } }
    }

    private fun load(source: WhatsNewSource, failures: FailureLog): WhatsNewUiState = try {
        WhatsNewUiState(source.releases().map { WhatsNewCard(it.version, formatReleaseDate(it.date), it.notes) })
    } catch (e: Exception) {
        failures.warn(TAG, "what's new did not load: ${e.message}", e)
        WhatsNewUiState(loadFailed = true)
    }
}
