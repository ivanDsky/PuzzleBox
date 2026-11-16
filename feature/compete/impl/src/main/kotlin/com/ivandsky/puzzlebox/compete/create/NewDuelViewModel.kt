package com.ivandsky.puzzlebox.compete.create

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivandsky.puzzlebox.auth.GoogleIdTokenProvider
import com.ivandsky.puzzlebox.data.calendar.GameCalendar
import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.data.identity.IdentityService
import com.ivandsky.puzzlebox.data.repository.DailyRepository
import com.ivandsky.puzzlebox.domain.CreateMatch
import com.ivandsky.puzzlebox.domain.CreateOutcome
import com.ivandsky.puzzlebox.domain.ObserveTodaySet
import com.ivandsky.puzzlebox.domain.TodayStatus
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.model.ShareCode
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Which panel the sheet shows. */
internal enum class DuelStep { Create, SignIn }

/** Why the last Create did not close the sheet; the draft is kept for each. */
internal sealed interface DuelNotice {
    data object Offline : DuelNotice
    /** The service's daily limit on new duels was reached; not a connection problem. */
    data object Busy : DuelNotice
    data object NoPuzzles : DuelNotice
    data class Failed(val message: String?) : DuelNotice
}

/** How the sign-in panel's last try went; a dismissed account picker is back to [Idle]. */
internal enum class DuelSignIn { Idle, SigningIn, Offline, Failed }

/** Everything the sheet draws. [summary] is the footer text, fixed at the moment the state was built. */
internal data class NewDuelState(
    val draft: DuelDraft,
    val games: List<DuelGame>,
    val summary: String,
    /** The friend a challenge invites, by name, for the sheet to say so; null for a duel nobody is invited to. */
    val invitee: String?,
    val step: DuelStep,
    val creating: Boolean,
    val notice: DuelNotice?,
    val signIn: DuelSignIn,
) {
    val canCreate: Boolean get() = draft.games.isNotEmpty() && !creating
}

private const val STOP_TIMEOUT_MS = 5_000L

/**
 * The New duel sheet's state holder: presets and customize edits on a [DuelDraft], Create through [createMatch], and
 * the Google sign-in a signed-out player gets first. A created duel's code leaves once through [created]; the sheet
 * calls [reset] when it closes so the next opening starts from the Quick duel again.
 */
internal class NewDuelViewModel(
    private val createMatch: CreateMatch,
    private val identity: IdentityService,
    idTokens: GoogleIdTokenProvider,
    todaySet: ObserveTodaySet,
    calendar: GameCalendar,
    daily: DailyRepository,
    private val registry: GameRegistry,
    private val clock: () -> Instant,
    private val zone: () -> ZoneId,
    private val locale: () -> Locale,
) : ViewModel() {

    private data class Working(
        val draft: DuelDraft,
        val creating: Boolean = false,
        val notice: DuelNotice? = null,
        val signInRequired: Boolean = false,
        val signIn: DuelSignIn = DuelSignIn.Idle,
        val generation: Int = 0,
        val challenge: DuelChallenge? = null,
    )

    private val signInStep = DuelSignInStep(identity, idTokens)
    private val working = MutableStateFlow(Working(DuelDraft.quick(emptyList())))
    private val createdCodes = Channel<Pair<ShareCode, List<String>>>(Channel.BUFFERED)

    private val games: StateFlow<List<DuelGame>> = todaySet().map { entries ->
        entries.map { entry ->
            DuelGame(
                id = entry.game,
                name = entry.displayName,
                todayNumber = calendar.today(entry.game)?.let { daily.puzzleNumber(entry.game, it) },
                solvedToday = entry.status is TodayStatus.Solved,
                hintsFirst = registry.rankOrderOf(entry.game) == RankOrder.HintsThenTime,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** The new duel's code and its puzzles' game names, one per puzzle in order, once, after Create succeeded. */
    val created: Flow<Pair<ShareCode, List<String>>> = createdCodes.receiveAsFlow()

    val state: StateFlow<NewDuelState> = combine(working, games, identity.current, ::build)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), build(working.value, emptyList(), identity.current.value))

    init {
        // The Quick duel names every game, which are known only once today's set first reads.
        viewModelScope.launch {
            val available = games.first { it.isNotEmpty() }
            working.update { if (it.draft.games.isEmpty()) it.copy(draft = DuelDraft.quick(available)) else it }
        }
    }

    private fun build(work: Working, available: List<DuelGame>, who: Identity?) = NewDuelState(
        draft = work.draft,
        games = available,
        summary = work.draft.summary(clock(), zone(), available, locale()),
        invitee = work.challenge?.friendName,
        step = if (who == null || work.signInRequired) DuelStep.SignIn else DuelStep.Create,
        creating = work.creating,
        notice = work.notice,
        signIn = work.signIn,
    )

    fun chooseQuick() = edit { DuelDraft.quick(games.value) }
    fun chooseDaily() = edit { DuelDraft.daily(games.value) }
    fun setCount(count: Int) = edit { it.withCount(count) }
    fun toggle(game: DuelGame) = edit { it.toggled(game) }
    fun setDeadline(hours: Int) = edit { it.withDeadline(hours) }
    fun setFriends(included: Boolean) = edit { it.withFriends(included) }

    /**
     * Opens as a challenge: [challenge]'s friend is invited, and the duel is one puzzle of its game. The same challenge
     * again (the sheet recomposed, the screen turned) leaves the draft as the player has edited it.
     */
    fun prefill(challenge: DuelChallenge) = working.update {
        if (it.challenge == challenge) it else it.copy(draft = DuelDraft.single(challenge.game), challenge = challenge, notice = null)
    }

    private fun edit(change: (DuelDraft) -> DuelDraft) = working.update { it.copy(draft = change(it.draft), notice = null) }

    /** Back to the Quick duel with nothing pending (no challenge either), as the sheet closes; a create still in flight is dropped. */
    fun reset() = working.update { Working(DuelDraft.quick(games.value), generation = it.generation + 1) }

    /** Create duel, one at a time; a created duel stays `creating` until [reset]. An outcome from before a [reset] is dropped. */
    fun create() {
        val start = working.getAndUpdate { if (it.creating) it else it.copy(creating = true, notice = null) }
        if (start.creating || start.draft.games.isEmpty()) return
        viewModelScope.launch {
            val outcome = createMatch(start.draft.toSettings(), invitedUid = start.challenge?.friendUid)
            var current = false
            working.update { work ->
                current = work.generation == start.generation
                if (!current) return@update work
                when (outcome) {
                    is CreateOutcome.Created -> work
                    CreateOutcome.SignedOut -> work.copy(creating = false, signInRequired = true)
                    CreateOutcome.Offline -> work.copy(creating = false, notice = DuelNotice.Offline)
                    CreateOutcome.DailyCapReached -> work.copy(creating = false, notice = DuelNotice.Busy)
                    CreateOutcome.NoPuzzles -> work.copy(creating = false, notice = DuelNotice.NoPuzzles)
                    is CreateOutcome.Failed -> work.copy(creating = false, notice = DuelNotice.Failed(outcome.message))
                }
            }
            if (current && outcome is CreateOutcome.Created) {
                createdCodes.send(outcome.code to outcome.legs.map { registry.displayName(it.ref.gameId) })
            }
        }
    }

    /**
     * Continue with Google: [activityContext] hosts the account picker and is not kept. A try under way is not
     * repeated. Success leaves the sign-in panel for the create panel with the draft as it was.
     */
    suspend fun signIn(activityContext: Context) {
        if (working.getAndUpdate { it.copy(signIn = DuelSignIn.SigningIn) }.signIn == DuelSignIn.SigningIn) return
        val failure = try {
            signInStep(activityContext)
        } catch (cancelled: CancellationException) {
            working.update { it.copy(signIn = DuelSignIn.Idle) }
            throw cancelled
        }
        working.update {
            it.copy(signIn = failure ?: DuelSignIn.Idle, signInRequired = it.signInRequired && failure != null)
        }
    }
}
