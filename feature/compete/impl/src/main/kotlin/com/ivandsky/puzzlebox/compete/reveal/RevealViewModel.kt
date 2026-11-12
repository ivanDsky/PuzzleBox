package com.ivandsky.puzzlebox.compete.reveal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivandsky.puzzlebox.data.match.Match
import com.ivandsky.puzzlebox.domain.MatchState
import com.ivandsky.puzzlebox.domain.MatchView
import com.ivandsky.puzzlebox.domain.RecordRevealSeen
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.gameui.GamePlayRegistry
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.ShareCode
import java.time.ZoneId
import java.util.Locale
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the reveal screen draws. */
internal sealed interface RevealUiState {
    data object Loading : RevealUiState

    data object NotFound : RevealUiState

    data object Offline : RevealUiState

    data object UpdateApp : RevealUiState

    data object SignedOut : RevealUiState

    /** Loaded with no puzzle settled yet, so nothing to race; a later load can still bring the race. */
    data object Unsettled : RevealUiState

    /**
     * The reveal of [match]. The [race] the machine plays and how its first run [start]s are fixed when the race
     * opens; [rerun] counts the Watch agains, each racing what is settled at that moment. The [summary] follows
     * the latest load.
     */
    data class Running(
        val match: Match,
        val race: RevealPlan,
        val start: RevealStart,
        val rerun: Int,
        val summary: DuelSummary,
    ) : RevealUiState
}

private const val STOP_TIMEOUT_MS = 5_000L

/**
 * The reveal screen's state holder for one [code]. The first load with a settled puzzle opens the race; a later
 * load, an offline blip or any other re-emission never restarts it, and a first opening records that the reveal was
 * seen through [recordSeen], once. Results that arrive later reach the summary only. [reducedMotion], [zone] and
 * [locale] are the screen's when it was created.
 */
internal class RevealViewModel(
    code: String,
    private val reducedMotion: Boolean,
    private val zone: ZoneId,
    private val locale: Locale,
    observeMatch: (ShareCode) -> Flow<MatchState>,
    private val registry: GameRegistry,
    val brands: GamePlayRegistry,
    private val recordSeen: RecordRevealSeen,
) : ViewModel() {

    /** The race the machine plays for one run, and how the first run started. */
    private class Race(val plan: RevealPlan, val start: RevealStart, val rerun: Int)

    private val shareCode: ShareCode? = ShareCode.parse(code)
    private val reruns = MutableStateFlow(0)
    private var race: Race? = null
    private var latest: MatchView? = null

    val state: StateFlow<RevealUiState> = if (shareCode == null) {
        MutableStateFlow<RevealUiState>(RevealUiState.NotFound).asStateFlow()
    } else {
        combine(observeMatch(shareCode), reruns) { match, rerun -> build(shareCode, match, rerun) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), RevealUiState.Loading)
    }

    /** Races again from the intro, over every puzzle settled by now. */
    fun watchAgain() = reruns.update { it + 1 }

    fun gameName(id: GameId): String = registry.displayName(id)

    /** A failure after a load keeps that load: the reveal never falls back to a message once it has a match. */
    private fun build(code: ShareCode, state: MatchState, rerun: Int): RevealUiState {
        val view = when (state) {
            is MatchState.Loaded -> state.view.also { latest = it }
            MatchState.NotFound -> latest ?: return RevealUiState.NotFound
            MatchState.Offline -> latest ?: return RevealUiState.Offline
            is MatchState.UpdateApp -> latest ?: return RevealUiState.UpdateApp
            is MatchState.SignedOut -> latest ?: return RevealUiState.SignedOut
        }
        val plan = revealPlan(view, registry::displayName, registry::rankOrderOf)
        val current = race
        val next = when {
            current == null && plan.cards.isEmpty() -> return RevealUiState.Unsettled
            current == null -> open(code, plan, view, rerun)
            current.rerun != rerun -> Race(plan, current.start, rerun)
            else -> current
        }
        race = next
        val summary = duelSummary(view.match, plan, view.outcome, zone, locale)
        return RevealUiState.Running(view.match, next.plan, next.start, next.rerun, summary)
    }

    private fun open(code: ShareCode, plan: RevealPlan, view: MatchView, rerun: Int): Race {
        val start = RevealStart.of(reducedMotion, view.local?.revealSeenAt)
        if (start.markSeen) viewModelScope.launch { recordSeen(code, view.outcome.myWins, view.outcome.theirWins) }
        return Race(plan, start, rerun)
    }
}
