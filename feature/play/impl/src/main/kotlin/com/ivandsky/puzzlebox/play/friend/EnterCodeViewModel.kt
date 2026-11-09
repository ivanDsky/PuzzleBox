package com.ivandsky.puzzlebox.play.friend

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivandsky.puzzlebox.common.dispatcher.DispatcherProvider
import com.ivandsky.puzzlebox.data.match.MatchFetch
import com.ivandsky.puzzlebox.data.match.MatchRepository
import com.ivandsky.puzzlebox.domain.LoadSharedPuzzle
import com.ivandsky.puzzlebox.domain.OpenOutcome
import com.ivandsky.puzzlebox.model.ShareCode
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Where the Enter code sheet's check of a code stands. */
internal sealed interface CodeCheck {
    data object Idle : CodeCheck

    data class Checking(val code: ShareCode) : CodeCheck

    /** [code] didn't open; [outcome] says why. */
    data class Failed(val code: ShareCode, val outcome: OpenOutcome) : CodeCheck

    /** [code] opened and is stored on this device, so its card opens without the network. */
    data class Opened(val code: ShareCode) : CodeCheck

    /** [code] is not a puzzle but is a duel: the sheet hides and the app opens it. */
    data class MatchOpened(val code: ShareCode) : CodeCheck
}

/**
 * The Enter code sheet's check: opens a code with [LoadSharedPuzzle] before the sheet hides, so
 * a code that can't open says why where it was typed; with duels on, a code that isn't a puzzle
 * is read as a duel through [MatchRepository]. One check runs at a time; a new one replaces it.
 */
internal class EnterCodeViewModel(
    private val load: LoadSharedPuzzle,
    private val matches: MatchRepository,
    private val dispatchers: DispatcherProvider,
) : ViewModel() {

    private val check = MutableStateFlow<CodeCheck>(CodeCheck.Idle)
    private var running: Job? = null

    val state: StateFlow<CodeCheck> = check.asStateFlow()

    /**
     * Checks [code] as a puzzle; when that says [OpenOutcome.NotFound] and [tryMatch] is set, looks
     * it up as a duel. Any other puzzle outcome is final.
     */
    fun open(code: ShareCode, tryMatch: Boolean) {
        running?.cancel()
        check.value = CodeCheck.Checking(code)
        running = viewModelScope.launch {
            val outcome = withContext(dispatchers.default) { load(code) }
            check.value = when {
                outcome is OpenOutcome.Opened -> CodeCheck.Opened(code)
                outcome == OpenOutcome.NotFound && tryMatch -> matchCheck(code)
                else -> CodeCheck.Failed(code, outcome)
            }
        }
    }

    private suspend fun matchCheck(code: ShareCode): CodeCheck = when (val fetch = matches.observe(code).first()) {
        is MatchFetch.Found -> CodeCheck.MatchOpened(code)
        MatchFetch.NotFound -> CodeCheck.Failed(code, OpenOutcome.NotFound)
        MatchFetch.Offline -> CodeCheck.Failed(code, OpenOutcome.Offline)
        is MatchFetch.UnsupportedVersion -> CodeCheck.Failed(code, OpenOutcome.NeedsUpdate(fetch.found))
    }

    /** The code was edited: a failure no longer describes it. */
    fun edited() {
        if (check.value is CodeCheck.Failed) check.value = CodeCheck.Idle
    }

    /** The sheet closed: drop any check, so the next showing starts fresh. */
    fun reset() {
        running?.cancel()
        check.value = CodeCheck.Idle
    }
}
