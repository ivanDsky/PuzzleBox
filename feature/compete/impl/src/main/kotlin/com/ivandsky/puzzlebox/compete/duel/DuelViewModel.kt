package com.ivandsky.puzzlebox.compete.duel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivandsky.puzzlebox.auth.GoogleIdTokenProvider
import com.ivandsky.puzzlebox.common.dispatcher.DispatcherProvider
import com.ivandsky.puzzlebox.common.flags.ReleaseFlags
import com.ivandsky.puzzlebox.common.flags.isEnabled
import com.ivandsky.puzzlebox.compete.CompeteFlag
import com.ivandsky.puzzlebox.compete.create.DuelSignIn
import com.ivandsky.puzzlebox.compete.create.DuelSignInStep
import com.ivandsky.puzzlebox.compete.domain.DeleteSelfDuel
import com.ivandsky.puzzlebox.compete.domain.DuelSnapshot
import com.ivandsky.puzzlebox.compete.domain.ObserveDuel
import com.ivandsky.puzzlebox.compete.domain.SeatAction
import com.ivandsky.puzzlebox.compete.domain.SeatResult
import com.ivandsky.puzzlebox.compete.domain.UpdateMatchSeat
import com.ivandsky.puzzlebox.compete.domain.lineup.LegName
import com.ivandsky.puzzlebox.compete.domain.lineup.ObserveLegTitles
import com.ivandsky.puzzlebox.compete.domain.lineup.SaveDuelPuzzles
import com.ivandsky.puzzlebox.data.identity.IdentityService
import com.ivandsky.puzzlebox.data.match.Match
import com.ivandsky.puzzlebox.data.match.MatchResultSync
import com.ivandsky.puzzlebox.data.match.Player
import com.ivandsky.puzzlebox.domain.LoadLegPuzzle
import com.ivandsky.puzzlebox.domain.MatchState
import com.ivandsky.puzzlebox.domain.MatchView
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.gameui.GamePlayRegistry
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
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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
    private val identity: IdentityService,
    idTokens: GoogleIdTokenProvider,
    private val registry: GameRegistry,
    deleteSelfDuel: DeleteSelfDuel,
    showTimeLeft: Flow<Boolean>,
    loadLegPuzzle: LoadLegPuzzle,
    plays: GamePlayRegistry,
    dispatchers: DispatcherProvider,
    private val legTitles: ObserveLegTitles,
    private val saveDuelPuzzles: SaveDuelPuzzles,
    releaseFlags: ReleaseFlags,
) : ViewModel() {

    /** Past you wears the player's photo in the header ([CompeteFlag.PastYouPhoto]), read once. */
    private val pastYouPhoto: Boolean = releaseFlags.isEnabled(CompeteFlag.PastYouPhoto)

    /** The boards of the duel's legs, for the tablet's detail column; kept while the screen is. */
    internal val legBoards: LegBoards = DuelLegBoards(loadLegPuzzle, plays, dispatchers)

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
    private val removal = SelfDuelRemoval(shareCode, deleteSelfDuel, viewModelScope) { closeEvents.send(Unit) }

    init {
        if (shareCode != null) resultSync.poke()
    }

    /** Fires once after a Decline or a Remove finished: the screen has nothing left to show. */
    val closes: Flow<Unit> = closeEvents.receiveAsFlow()

    /** Opens the remove-confirm of a self duel. */
    fun askRemove() = removal.ask()

    /** Closes the remove-confirm and keeps the duel. */
    fun keep() = removal.keep()

    /** Removes the self duel the confirm asked about; [closes] fires when it is gone. */
    fun remove() = removal.remove()

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<DuelUiState> = if (shareCode == null) {
        MutableStateFlow<DuelUiState>(DuelUiState.NotFound).asStateFlow()
    } else {
        val duels = reloads.flatMapLatest { observeDuel(shareCode) }
            .shareIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), replay = 1)
        combine(
            duels.combine(duels.legTitlesBy(legTitles)) { duel, titles -> duel to titles },
            working,
            identity.current,
            removal.step,
            showTimeLeft,
        ) { (duel, names), work, me, step, timeLeft ->
            build(shareCode, duel, names, work, me?.displayName, me?.photoUrl.takeIf { pastYouPhoto }, step, timeLeft)
        }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), DuelUiState.Loading)
    }

    private fun build(
        code: ShareCode,
        duel: DuelSnapshot,
        names: Map<ShareCode, LegName>,
        work: Working,
        myName: String?,
        myPhotoUrl: String?,
        removal: RemovalStep,
        timeLeft: Boolean,
    ): DuelUiState {
        if (removal == RemovalStep.Removing) return DuelUiState.Loading
        return when (val match = duel.match) {
            MatchState.NotFound -> DuelUiState.NotFound
            MatchState.Offline -> DuelUiState.Offline
            is MatchState.UpdateApp -> DuelUiState.UpdateApp
            is MatchState.SignedOut -> DuelUiState.Invite(
                InviteKind.SignedOut, code, match.match?.let { inviteDetails(it, null, duel.solvedBefore, names) }, false, null, work.signIn,
            )
            is MatchState.Loaded -> loaded(code, match.view, duel, names, work, myName, myPhotoUrl, removal == RemovalStep.Asking, timeLeft)
        }
    }

    private fun loaded(
        code: ShareCode,
        view: MatchView,
        duel: DuelSnapshot,
        names: Map<ShareCode, LegName>,
        work: Working,
        myName: String?,
        myPhotoUrl: String?,
        removeAsked: Boolean,
        timeLeftShown: Boolean,
    ): DuelUiState {
        val match = view.match
        val me = view.me
        val challenger = match.challenger.uid == me
        if (match.opponent?.uid == me || challenger && match.opponent != null) {
            return DuelUiState.Participant(
                view = view,
                elapsedMs = duel.elapsedMs,
                firstSolvedAt = duel.firstSolvedAt,
                myName = myName,
                removeAsked = removeAsked,
                timeLeftShown = timeLeftShown,
                names = names,
                pastYouPhotoUrl = myPhotoUrl,
            )
        }
        val kind = work.outcome ?: when {
            match.opponent != null -> InviteKind.Taken
            match.cancelled || match.declined -> InviteKind.Cancelled
            challenger -> InviteKind.Waiting
            else -> InviteKind.Open
        }
        val details = inviteDetails(match, me, duel.solvedBefore, names)
        return DuelUiState.Invite(kind, code, details, work.seat != null, work.notice, work.signIn)
    }

    /** The card's facts; [me] is null signed out, when the viewer is neither challenger nor invitee. */
    private fun inviteDetails(match: Match, me: String?, solvedBefore: Boolean, names: Map<ShareCode, LegName>) = InviteDetails(
        challenger = match.challenger,
        rounds = match.legs.size,
        deadline = match.deadline,
        viewerIsChallenger = match.challenger.uid == me,
        declined = match.declined,
        solvedBefore = solvedBefore,
        canDecline = me != null && match.invitedUid == me,
        lineup = inviteLegs(match.legs.map { it.ref }, registry::displayName, names.mapValues { it.value.title }),
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
        // Read before the call: once the seat is mine the card is gone.
        val exact = (state.value as? DuelUiState.Invite)?.details?.lineup.orEmpty().mapNotNull { it.code }
        viewModelScope.launch {
            val result = updateSeat(code, action)
            working.update { it.after(action, result) }
            if (action == SeatAction.Claim && result == SeatResult.Done) saveDuelPuzzles(exact)
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
