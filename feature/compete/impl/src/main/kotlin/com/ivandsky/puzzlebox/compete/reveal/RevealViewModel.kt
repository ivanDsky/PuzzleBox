package com.ivandsky.puzzlebox.compete.reveal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivandsky.puzzlebox.data.match.Match
import com.ivandsky.puzzlebox.domain.MatchState
import com.ivandsky.puzzlebox.domain.LegVerdict
import com.ivandsky.puzzlebox.domain.LoadSolveReplay
import com.ivandsky.puzzlebox.domain.MatchView
import com.ivandsky.puzzlebox.domain.RecordRevealSeen
import com.ivandsky.puzzlebox.domain.SolveReplayRef
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.game.replay.Lead
import com.ivandsky.puzzlebox.game.replay.Timeline
import com.ivandsky.puzzlebox.gameui.GamePlayRegistry
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.ShareCode
import java.time.ZoneId
import java.util.Locale
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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
     * the latest load. [compareLegs] are the legs whose replays can be compared: none before the reveal has run
     * (no spoiler), then every settled leg not found without a replay on both sides.
     */
    data class Running(
        val match: Match,
        val race: RevealPlan,
        val start: RevealStart,
        val rerun: Int,
        val summary: DuelSummary,
        val compareLegs: Set<Int>,
    ) : RevealUiState
}

/**
 * The replays of one leg side by side: either side null when it has none. [finalLead] is the verdict the reveal
 * shows for the leg, so the lead line and the result agree at the end.
 */
internal class LegCompare(
    val leg: Int,
    val mine: Timeline?,
    val theirs: Timeline?,
    val opponent: String,
    val finalLead: Lead,
)

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
    private val loadReplay: LoadSolveReplay,
) : ViewModel() {

    /** The race the machine plays for one run, and how the first run started. */
    private class Race(val plan: RevealPlan, val start: RevealStart, val rerun: Int)

    private val shareCode: ShareCode? = ShareCode.parse(code)
    private val reruns = MutableStateFlow(0)
    private val ran = MutableStateFlow(false)
    private val withoutReplay = MutableStateFlow<Set<Int>>(emptySet())
    private val _compare = MutableStateFlow<LegCompare?>(null)
    private var opening: Job? = null
    private var race: Race? = null
    private var latest: MatchView? = null

    val state: StateFlow<RevealUiState> = if (shareCode == null) {
        MutableStateFlow<RevealUiState>(RevealUiState.NotFound).asStateFlow()
    } else {
        combine(observeMatch(shareCode), reruns, ran, withoutReplay) { match, rerun, ended, none ->
            build(shareCode, match, rerun, if (ended) none else null)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), RevealUiState.Loading)
    }

    /** The compare open on one leg, or null. */
    val compare: StateFlow<LegCompare?> = _compare.asStateFlow()

    /** Races again from the intro, over every puzzle settled by now. The compare is a spoiler again until it ends. */
    fun watchAgain() {
        closeCompare()
        ran.value = false
        reruns.update { it + 1 }
    }

    /** The screen calls this once the race has reached its summary; until then no leg offers a compare. */
    fun revealEnded() {
        ran.value = true
    }

    /**
     * Opens the compare of [leg] if the state offers it: my replay and theirs are read now, theirs online. A leg
     * with neither stops being offered; closing is [closeCompare].
     */
    fun openCompare(leg: Int) {
        val view = latest ?: return
        val current = race ?: return
        val lead = view.outcome.legs.getOrNull(leg)?.let(::finalLead)
        val code = shareCode
        if (!ran.value || lead == null || code == null || leg in withoutReplay.value) return
        closeCompare()
        opening = viewModelScope.launch {
            val (mine, theirs) = coroutineScope {
                val mine = async { loadReplay(SolveReplayRef.MyLeg(code, leg)) }
                val theirs = async { loadReplay(SolveReplayRef.TheirLeg(code, leg)) }
                mine.await() to theirs.await()
            }
            if (mine == null && theirs == null) {
                withoutReplay.update { it + leg }
            } else {
                _compare.value = LegCompare(leg, mine, theirs, current.plan.opponent.name, lead)
            }
        }
    }

    /** Drops both timelines. */
    fun closeCompare() {
        opening?.cancel()
        _compare.value = null
    }

    fun gameName(id: GameId): String = registry.displayName(id)

    /** A failure after a load keeps that load: the reveal never falls back to a message once it has a match. */
    private fun build(code: ShareCode, state: MatchState, rerun: Int, withoutReplay: Set<Int>?): RevealUiState {
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
        val compareLegs = if (withoutReplay == null) emptySet() else comparableLegs(view) - withoutReplay
        return RevealUiState.Running(view.match, next.plan, next.start, next.rerun, summary, compareLegs)
    }

    private fun comparableLegs(view: MatchView): Set<Int> =
        view.outcome.legs.indices.filterTo(HashSet()) { finalLead(view.outcome.legs[it]) != null }

    private fun open(code: ShareCode, plan: RevealPlan, view: MatchView, rerun: Int): Race {
        val start = RevealStart.of(reducedMotion, view.local?.revealSeenAt)
        if (start.markSeen) viewModelScope.launch { recordSeen(code, view.outcome.myWins, view.outcome.theirWins) }
        return Race(plan, start, rerun)
    }
}

/** The verdict the reveal shows for a leg, as the lead line's last word; null where nothing was decided. */
private fun finalLead(verdict: LegVerdict): Lead? = when (verdict) {
    is LegVerdict.Win -> Lead.Mine
    is LegVerdict.Loss -> Lead.Theirs
    LegVerdict.Draw -> Lead.Level
    LegVerdict.Void, LegVerdict.Pending -> null
}
