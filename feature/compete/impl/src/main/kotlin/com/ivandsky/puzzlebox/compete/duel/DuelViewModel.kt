package com.ivandsky.puzzlebox.compete.duel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivandsky.puzzlebox.auth.GoogleIdTokenProvider
import com.ivandsky.puzzlebox.compete.create.DuelSignIn
import com.ivandsky.puzzlebox.compete.create.DuelSignInStep
import com.ivandsky.puzzlebox.data.identity.IdentityService
import com.ivandsky.puzzlebox.data.match.Match
import com.ivandsky.puzzlebox.data.match.MatchResultSync
import com.ivandsky.puzzlebox.data.match.Player
import com.ivandsky.puzzlebox.domain.DuelSnapshot
import com.ivandsky.puzzlebox.domain.MatchState
import com.ivandsky.puzzlebox.domain.MatchView
import com.ivandsky.puzzlebox.domain.ObserveDuel
import com.ivandsky.puzzlebox.domain.SeatAction
import com.ivandsky.puzzlebox.domain.SeatResult
import com.ivandsky.puzzlebox.domain.UpdateMatchSeat
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.ShareCode
import java.time.Instant
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the invite card tells a viewer who holds no seat: the tail below the card follows the kind. */
internal enum class InviteKind { SignedOut, Open, Waiting, Taken, Cancelled }

/** Why the last seat call did not finish, and which [action] it was; the invite stays so the viewer can try again. */
internal sealed interface InviteNotice {
    val action: SeatAction

    data class Offline(override val action: SeatAction) : InviteNotice
    data class Closed(override val action: SeatAction) : InviteNotice
    data class Failed(override val action: SeatAction, val message: String?) : InviteNotice
}

/** One game of the duel, by id and name, for the card's chips. */
internal data class InviteGame(val id: GameId, val name: String)

/**
 * The card's facts about the duel: who [challenger] is, the [games] (one per game, in puzzle order), the [rounds] to
 * play and when it ends. [viewerIsChallenger] is a duel the viewer opened; [declined] a rematch the invitee refused;
 * [solvedBefore] when the viewer already has a completion of one of the puzzles; [canDecline] when the invite is
 * addressed to the viewer.
 */
internal data class InviteDetails(
    val challenger: Player,
    val games: List<InviteGame>,
    val rounds: Int,
    val deadline: Instant,
    val viewerIsChallenger: Boolean,
    val declined: Boolean,
    val solvedBefore: Boolean,
    val canDecline: Boolean,
)

/** What the duel screen draws. */
internal sealed interface DuelUiState {
    data object Loading : DuelUiState

    /** No seat held. [details] is null only while signed out with the match unread. [working] is a seat call in flight. */
    data class Invite(
        val kind: InviteKind,
        val code: ShareCode,
        val details: InviteDetails?,
        val working: Boolean,
        val notice: InviteNotice?,
        val signIn: DuelSignIn,
    ) : DuelUiState

    /** The viewer holds a seat; the puzzles screen takes [view] and the saved plays' [elapsedMs], by puzzle index. */
    data class Participant(val view: MatchView, val elapsedMs: Map<Int, Long>) : DuelUiState

    data object NotFound : DuelUiState
    data object Offline : DuelUiState
    data object UpdateApp : DuelUiState
}

private const val STOP_TIMEOUT_MS = 5_000L

/**
 * The duel screen's state holder for one [code]: [ObserveDuel] mapped to a [DuelUiState], the seat actions through
 * [updateSeat], and the Google sign-in a signed-out viewer gets first. A seat call is one at a time. [closes] fires
 * once when the screen is done with its viewer: after a Decline. Opening the duel asks [resultSync] to retry the
 * result posts still pending.
 */
internal class DuelViewModel(
    code: String,
    observeDuel: ObserveDuel,
    private val updateSeat: UpdateMatchSeat,
    resultSync: MatchResultSync,
    identity: IdentityService,
    idTokens: GoogleIdTokenProvider,
    private val registry: GameRegistry,
) : ViewModel() {

    private data class Working(
        val seat: SeatAction? = null,
        val notice: InviteNotice? = null,
        val outcome: InviteKind? = null,
        val signIn: DuelSignIn = DuelSignIn.Idle,
    )

    private val signInStep = DuelSignInStep(identity, idTokens)
    private val shareCode: ShareCode? = ShareCode.parse(code)
    private val working = MutableStateFlow(Working())
    private val reloads = MutableStateFlow(0)
    private val closeEvents = Channel<Unit>(Channel.BUFFERED)

    init {
        if (shareCode != null) resultSync.poke()
    }

    /** Fires once after a Decline finished: the screen has nothing left to show. */
    val closes: Flow<Unit> = closeEvents.receiveAsFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<DuelUiState> = if (shareCode == null) {
        MutableStateFlow<DuelUiState>(DuelUiState.NotFound).asStateFlow()
    } else {
        combine(reloads.flatMapLatest { observeDuel(shareCode) }, working) { duel, work -> build(shareCode, duel, work) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), DuelUiState.Loading)
    }

    private fun build(code: ShareCode, duel: DuelSnapshot, work: Working): DuelUiState = when (val match = duel.match) {
        MatchState.NotFound -> DuelUiState.NotFound
        MatchState.Offline -> DuelUiState.Offline
        is MatchState.UpdateApp -> DuelUiState.UpdateApp
        is MatchState.SignedOut -> DuelUiState.Invite(
            InviteKind.SignedOut, code, match.match?.let { inviteDetails(it, null, duel.solvedBefore) }, false, null, work.signIn,
        )
        is MatchState.Loaded -> loaded(code, match.view, duel, work)
    }

    private fun loaded(code: ShareCode, view: MatchView, duel: DuelSnapshot, work: Working): DuelUiState {
        val match = view.match
        val me = view.me
        val challenger = match.challenger.uid == me
        if (match.opponent?.uid == me || challenger && match.opponent != null) {
            return DuelUiState.Participant(view, duel.elapsedMs)
        }
        val kind = work.outcome ?: when {
            match.opponent != null -> InviteKind.Taken
            match.cancelled || match.declined -> InviteKind.Cancelled
            challenger -> InviteKind.Waiting
            else -> InviteKind.Open
        }
        val details = inviteDetails(match, me, duel.solvedBefore)
        return DuelUiState.Invite(kind, code, details, work.seat != null, work.notice, work.signIn)
    }

    /** The card's facts; [me] is null signed out, when the viewer is neither challenger nor invitee. */
    private fun inviteDetails(match: Match, me: String?, solvedBefore: Boolean) = InviteDetails(
        challenger = match.challenger,
        games = match.legs.map { it.ref.gameId }.distinct().map { InviteGame(it, registry.displayName(it)) },
        rounds = match.legs.size,
        deadline = match.deadline,
        viewerIsChallenger = match.challenger.uid == me,
        declined = match.declined,
        solvedBefore = solvedBefore,
        canDecline = me != null && match.invitedUid == me,
    )

    /** Reads the duel again after an Offline. */
    fun retry() = reloads.update { it + 1 }

    fun accept() = seat(SeatAction.Claim)

    fun cancel() = seat(SeatAction.Cancel)

    fun decline() = seat(SeatAction.Decline)

    /** One seat call at a time; its result is applied unless the screen is gone. */
    private fun seat(action: SeatAction) {
        val code = shareCode ?: return
        if (working.getAndUpdate { if (it.seat == null) it.copy(seat = action, notice = null) else it }.seat != null) return
        viewModelScope.launch {
            val result = updateSeat(code, action)
            working.update { it.after(action, result) }
            if (action == SeatAction.Decline && result == SeatResult.Done) closeEvents.send(Unit)
        }
    }

    /** Done leaves the observed match to move the screen on; a lost race pins the card to what it lost to. */
    private fun Working.after(action: SeatAction, result: SeatResult): Working = when (result) {
        SeatResult.Done, SeatResult.SignedOut -> copy(seat = null)
        SeatResult.Taken -> copy(seat = null, outcome = InviteKind.Taken)
        SeatResult.Cancelled -> copy(seat = null, outcome = InviteKind.Cancelled)
        SeatResult.Closed -> copy(seat = null, notice = InviteNotice.Closed(action))
        SeatResult.Offline -> copy(seat = null, notice = InviteNotice.Offline(action))
        is SeatResult.Rejected -> copy(seat = null, notice = InviteNotice.Failed(action, result.message))
    }

    /**
     * Sign in with Google: [activityContext] hosts the account picker and is not kept. A try under way is not
     * repeated. Success needs no step here: the observed match re-reads under the new account.
     */
    suspend fun signIn(activityContext: Context) {
        if (working.getAndUpdate { it.copy(signIn = DuelSignIn.SigningIn) }.signIn == DuelSignIn.SigningIn) return
        val failure = try {
            signInStep(activityContext)
        } catch (cancelled: CancellationException) {
            working.update { it.copy(signIn = DuelSignIn.Idle) }
            throw cancelled
        }
        working.update { it.copy(signIn = failure ?: DuelSignIn.Idle) }
    }
}
