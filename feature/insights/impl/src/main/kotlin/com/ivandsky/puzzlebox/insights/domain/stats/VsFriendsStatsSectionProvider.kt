package com.ivandsky.puzzlebox.insights.domain.stats

import com.ivandsky.puzzlebox.common.log.FailureLog
import com.ivandsky.puzzlebox.common.log.LogcatFailureLog
import com.ivandsky.puzzlebox.data.identity.IdentityService
import com.ivandsky.puzzlebox.domain.guardedAsk
import com.ivandsky.puzzlebox.domain.ranks.OverallState
import com.ivandsky.puzzlebox.insights.domain.recap.FriendsOverallRead
import com.ivandsky.puzzlebox.insights.domain.recap.ResolveFriendsStanding
import com.ivandsky.puzzlebox.insights.domain.recap.rows
import com.ivandsky.puzzlebox.insights.domain.recap.settledEach
import kotlin.time.Duration
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

/**
 * The vs friends section: the viewer's best and weakest game by Friends Overall points ([friendsOverall]) over the
 * range's window (A15), the one stats section besides rank history that reaches the network. The window is the
 * current Mon–Sun week for the Week range and the current month for Month and All (Ranks has no all-time window), see
 * [StatsRange.friendsWindow]. Each game of the page's scope is read once, together, through the same settled read the
 * recap's friends card uses; one game (a single game's page, or one game with points) shows only the best.
 *
 * - A range with no counted solve has nothing to compare: the section is left out, like the local ones.
 * - Signed out, or signed in with nobody but the viewer on the boards: [VsFriendsStanding.Locked], which the screen
 *   draws as one plain line in the section's place saying what unlocks it.
 * - Signed in: [SectionState.Loading] first, then the standing. Friends but no points for the viewer in the window:
 *   the section is left out.
 * - A read that is partial or offline, fails, or takes longer than [timeout]: [SectionState.Unavailable] (A16), never a
 *   number a later read could change; the rest of the page is unaffected.
 *
 * Signing in or out while the page is open re-reads.
 */
internal class VsFriendsStatsSectionProvider(
    private val identity: IdentityService,
    private val friendsOverall: FriendsOverallRead,
    private val failures: FailureLog = LogcatFailureLog,
    private val timeout: Duration = ResolveFriendsStanding.READ_TIMEOUT,
) : StatsSectionProvider {

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun section(input: StatsInput): Flow<StatsSection?> =
        if (input.facts.solved == 0) {
            flow { emit(null) }
        } else {
            identity.current.map { it?.uid }.distinctUntilChanged().flatMapLatest { viewer -> read(input, viewer) }
        }

    private fun read(input: StatsInput, viewer: String?): Flow<StatsSection?> = flow {
        if (viewer == null) {
            emit(locked(VsFriendsStanding.Unlock.SignIn))
            return@flow
        }
        emit(StatsSection.VsFriends(SectionState.Loading))
        val answer = guardedAsk(failures, TAG, "vs friends", timeout) { compare(input, viewer) } ?: SectionState.Unavailable
        when (answer) {
            is SectionState.Ready -> emit(answer.value?.let { StatsSection.VsFriends(SectionState.Ready(it)) })
            else -> emit(StatsSection.VsFriends(SectionState.Unavailable))
        }
    }

    /** The comparison from the settled reads: Ready with null when there is nothing to say, Unavailable when a board could not be read. */
    private suspend fun compare(input: StatsInput, viewer: String): SectionState<VsFriendsStanding?> {
        val games = input.facts.scope?.let(::listOf) ?: input.games
        val reads = friendsOverall.settledEach(input.range.friendsWindow(input.today), games)
        val states = reads.map { it.second }
        val rows = states.flatMap { it.rows() }
        return when {
            OverallState.SignedOut in states -> SectionState.Ready(VsFriendsStanding.Locked(VsFriendsStanding.Unlock.SignIn))
            states.any { it !is OverallState.Complete && it != OverallState.Empty } -> SectionState.Unavailable
            rows.isEmpty() -> SectionState.Ready(null)
            rows.all { it.uid == viewer } -> SectionState.Ready(VsFriendsStanding.Locked(VsFriendsStanding.Unlock.AddFriends))
            else -> SectionState.Ready(VsFriendsStanding.Ranked.of(reads.mapNotNull { (game, state) -> (state as? OverallState.Complete)?.let { game to it.standings } }, viewer))
        }
    }

    private fun locked(unlock: VsFriendsStanding.Unlock) = StatsSection.VsFriends(SectionState.Ready(VsFriendsStanding.Locked(unlock)))

    private companion object {
        const val TAG = "VsFriendsStats"
    }
}
