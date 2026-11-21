package com.ivandsky.puzzlebox.compete.reveal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivandsky.puzzlebox.compete.domain.RecordRevealSeen
import com.ivandsky.puzzlebox.data.identity.IdentityService
import com.ivandsky.puzzlebox.data.match.Match
import com.ivandsky.puzzlebox.data.match.isSelf
import com.ivandsky.puzzlebox.domain.LegReplayLoad
import com.ivandsky.puzzlebox.domain.LegVerdict
import com.ivandsky.puzzlebox.domain.LoadSolveReplay
import com.ivandsky.puzzlebox.domain.MatchState
import com.ivandsky.puzzlebox.domain.MatchView
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
     * (no spoiler), then every settled leg not found without a replay on both sides. [noReplay] are a self duel's legs
     * whose first solve has no replay, once the reveal has run.
     */
    data class Running(
        val match: Match,
        val race: RevealPlan,
        val start: RevealStart,
        val rerun: Int,
        val summary: DuelSummary,
        val compareLegs: Set<Int>,
        val noReplay: Set<Int> = emptySet(),
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
    val me: String = MY_NAME,
    /** My photo for Past you's side of the compare (a self duel, flag on); null draws the initial. */
    val pastYouPhotoUrl: String? = null,
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
    private val identity: IdentityService,
) : ViewModel() {

    /** The race the machine plays for one run, and how the first run started. */
    private class Race(val plan: RevealPlan, val start: RevealStart, val rerun: Int)

    private val shareCode: ShareCode? = ShareCode.parse(code)
    private val reruns = MutableStateFlow(0)
    private val ran = MutableStateFlow(false)
    private val withoutReplay = MutableStateFlow<Set<Int>>(emptySet())

    /** Self duel legs whose first solve has no replay; kept across Watch agains, since a replay never appears later. */
    private val noPastReplay = MutableStateFlow<Set<Int>>(emptySet())

    /** The compare may be offered: the reveal ended and, in a self duel, the first solves' replays were probed. */
    private val revealed = MutableStateFlow(false)
    private val _compare = MutableStateFlow<LegCompare?>(null)
    private var opening: Job? = null
    private var probing: Job? = null
    private var race: Race? = null
    private var latest: MatchView? = null

    val state: StateFlow<RevealUiState> = if (shareCode == null) {
        MutableStateFlow<RevealUiState>(RevealUiState.NotFound).asStateFlow()
    } else {
        combine(observeMatch(shareCode), reruns, revealed, withoutReplay, noPastReplay) { match, rerun, offered, none, noPast ->
            build(shareCode, match, rerun, offered, none, noPast)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), RevealUiState.Loading)
    }

    /** The compare open on one leg, or null. */
    val compare: StateFlow<LegCompare?> = _compare.asStateFlow()

    /** Races again from the intro, over every puzzle settled by now. The compare is a spoiler again until it ends. */
    fun watchAgain() {
        closeCompare()
        probing?.cancel()
        withoutReplay.value = emptySet()
        ran.value = false
        revealed.value = false
        reruns.update { it + 1 }
    }

    /** The screen calls this once the race has reached its summary; until then no leg offers a compare. */
    fun revealEnded() {
        ran.value = true
        val view = latest
        val code = shareCode
        if (view != null && code != null && view.match.isSelf) probePastReplays(view, code) else revealed.value = true
    }

    /**
     * Self duels only: reads each leg's first-solve replay once the reveal ended, and rules out the legs found without one
     * so the summary says so instead of offering a Compare; the compare is offered once the probe is done. A failed read
     * leaves the leg offered, like [openCompare].
     */
    private fun probePastReplays(view: MatchView, code: ShareCode) {
        probing?.cancel()
        probing = viewModelScope.launch {
            for (leg in comparableLegs(view)) {
                if (leg in noPastReplay.value) continue
                if (loadReplay.readLeg(SolveReplayRef.TheirLeg(code, leg)) == LegReplayLoad.Absent) noPastReplay.update { it + leg }
            }
            revealed.value = true
        }
    }

    /**
     * Opens the compare of [leg] if the state offers it: my replay and theirs are read now, theirs online. A leg
     * with neither replay stops being offered, but only when both reads settled; a failed read (offline, a puzzle
     * that could not be fetched) keeps the leg offered so a later tap retries. Closing is [closeCompare].
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
                val mine = async { loadReplay.readLeg(SolveReplayRef.MyLeg(code, leg)) }
                val theirs = async { loadReplay.readLeg(SolveReplayRef.TheirLeg(code, leg)) }
                mine.await() to theirs.await()
            }
            val mineTimeline = (mine as? LegReplayLoad.Opened)?.timeline
            val theirTimeline = (theirs as? LegReplayLoad.Opened)?.timeline
            when {
                mineTimeline != null || theirTimeline != null ->
                    _compare.value = LegCompare(
                        leg, mineTimeline, theirTimeline, current.plan.opponent.name, lead,
                        me = if (current.plan.pastYou) current.plan.me.name else MY_NAME,
                        pastYouPhotoUrl = current.plan.pastYouPhotoUrl,
                    )
                mine == LegReplayLoad.Absent && theirs == LegReplayLoad.Absent -> withoutReplay.update { it + leg }
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
    private fun build(code: ShareCode, state: MatchState, rerun: Int, offered: Boolean, withoutReplay: Set<Int>, noPastReplay: Set<Int>): RevealUiState {
        val view = when (state) {
            is MatchState.Loaded -> state.view.also { latest = it }
            MatchState.NotFound -> latest ?: return RevealUiState.NotFound
            MatchState.Offline -> latest ?: return RevealUiState.Offline
            is MatchState.UpdateApp -> latest ?: return RevealUiState.UpdateApp
            is MatchState.SignedOut -> latest ?: return RevealUiState.SignedOut
        }
        val me = identity.current.value
        val plan = revealPlan(
            view, registry::displayName, registry::rankOrderOf, ownName = me?.displayName, pastYouPhotoUrl = me?.photoUrl,
        )
        val current = race
        val next = when {
            current == null && plan.cards.isEmpty() -> return RevealUiState.Unsettled
            current == null -> open(code, plan, view, rerun)
            current.rerun != rerun -> Race(plan, current.start, rerun)
            else -> current
        }
        race = next
        val summary = duelSummary(view.match, plan, view.outcome, zone, locale)
        val noReplay = if (offered) noPastReplay else emptySet()
        val compareLegs = if (!offered) emptySet() else comparableLegs(view) - withoutReplay - noPastReplay
        return RevealUiState.Running(view.match, next.plan, next.start, next.rerun, summary, compareLegs, noReplay)
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
