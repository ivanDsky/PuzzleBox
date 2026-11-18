package com.ivandsky.puzzlebox.compete.create

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivandsky.puzzlebox.auth.GoogleIdTokenProvider
import com.ivandsky.puzzlebox.common.flags.ReleaseFlag
import com.ivandsky.puzzlebox.common.flags.ReleaseFlags
import com.ivandsky.puzzlebox.common.flags.isEnabled
import com.ivandsky.puzzlebox.compete.create.lineup.LineupDraft
import com.ivandsky.puzzlebox.compete.create.lineup.LineupEditor
import com.ivandsky.puzzlebox.data.calendar.GameCalendar
import com.ivandsky.puzzlebox.data.identity.IdentityService
import com.ivandsky.puzzlebox.data.repository.DailyRepository
import com.ivandsky.puzzlebox.domain.CreateMatch
import com.ivandsky.puzzlebox.domain.ComputeSolvedPool
import com.ivandsky.puzzlebox.domain.CreateOutcome
import com.ivandsky.puzzlebox.domain.CreateSelfDuel
import com.ivandsky.puzzlebox.domain.ObserveTodaySet
import com.ivandsky.puzzlebox.domain.lineup.AnyGame
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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val STOP_TIMEOUT_MS = 5_000L

/**
 * The New duel sheet's state holder: presets and customize edits on a [DuelDraft], Create through [createMatch], and
 * the Google sign-in a signed-out player gets first. "Play against" also offers Past you (unless a challenge already names a friend):
 * its choices live on a [SelfDuelForm] measured against the solved pool ([SelfDuelStep]),
 * Create goes through it too and the new duel's code leaves once through [opened] (it has nothing to share).
 * A created friend duel's code leaves once through [created]; the sheet calls [reset] when it closes so the next
 * opening starts from the Quick duel again. With [releaseFlags] on for DuelLineup, Customize edits a [LineupEditor]
 * and Create sends its puzzles in order.
 */
internal class NewDuelViewModel(
    private val createMatch: CreateMatch,
    private val identity: IdentityService,
    idTokens: GoogleIdTokenProvider,
    todaySet: ObserveTodaySet,
    calendar: GameCalendar,
    daily: DailyRepository,
    private val registry: GameRegistry,
    createSelfDuel: CreateSelfDuel,
    computeSolvedPool: ComputeSolvedPool,
    clock: () -> Instant,
    zone: () -> ZoneId,
    locale: () -> Locale,
    private val releaseFlags: ReleaseFlags? = null,
) : ViewModel() {

    private val signInStep = DuelSignInStep(identity, idTokens)
    private val selfStep = SelfDuelStep(computeSolvedPool, createSelfDuel)
    private val working = MutableStateFlow(DuelWorking(DuelDraft.quick(emptyList())))
    private val createdCodes = Channel<CreatedDuel>(Channel.BUFFERED)
    private val openedCodes = Channel<ShareCode>(Channel.BUFFERED)

    private val games: StateFlow<List<DuelGame>> = todaySet().map { entries ->
        entries.map { entry ->
            val today = calendar.today(entry.game)
            DuelGame(
                id = entry.game,
                name = entry.displayName,
                todayNumber = today?.let { daily.puzzleNumber(entry.game, it) },
                todayDay = today,
                solvedToday = entry.status is TodayStatus.Solved,
                hintsFirst = registry.rankOrderOf(entry.game) == RankOrder.HintsThenTime,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** The new duel, once, after Create succeeded: a duel created as the sheet closed waits here for the next opening. */
    val created: Flow<CreatedDuel> = createdCodes.receiveAsFlow()

    /** The code of a duel against Past you, once, after Create succeeded: the sheet opens that duel, the next opening's if it closed first. */
    val opened: Flow<ShareCode> = openedCodes.receiveAsFlow()

    /** Bumped each time the sheet opens, so the solved counts are read again for puzzles solved since. */
    private val sheetOpens = MutableStateFlow(0)

    private val solved: StateFlow<SolvedCounts?> = selfStep
        .solved(
            games = games,
            includeFriends = working.map { it.form.includeFriends }.distinctUntilChanged(),
            rereads = combine(working.map { it.generation }.distinctUntilChanged(), sheetOpens, ::Pair),
        )
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val lineupOn: Flow<Boolean> = releaseFlags?.enabled?.map { ReleaseFlag.DuelLineup in it } ?: flowOf(false)

    private val builder = NewDuelStateBuilder(clock, zone, locale)

    val state: StateFlow<NewDuelState> = combine(working, games, identity.current, solved, lineupOn, builder::build)
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            builder.build(working.value, emptyList(), identity.current.value, solved.value, lineupOn = false),
        )

    init {
        // The Quick duel names every game, which are known only once today's set first reads.
        viewModelScope.launch {
            val available = games.first { it.isNotEmpty() }
            working.update {
                if (it.draft.games.isEmpty()) {
                    it.copy(draft = DuelDraft.quick(available), form = SelfDuelForm.quick(available), lineup = LineupEditor.opening(available))
                } else {
                    it
                }
            }
        }
    }

    private fun lineupEnabled() = releaseFlags?.isEnabled(ReleaseFlag.DuelLineup) == true

    private fun selfActive(work: DuelWorking) = work.opponent == Opponent.PastYou && work.challenge == null

    /** Past you or a friend: the choice is kept apart, so each keeps its own draft while the other is shown. */
    fun chooseOpponent(opponent: Opponent) = working.update { it.withoutNotice().copy(opponent = opponent) }

    fun chooseQuick() = edit(friend = { DuelDraft.quick(games.value) }, self = { SelfDuelForm.quick(games.value) })

    /** Daily, with the lineup on, shows today's puzzles as rows; coming from Customize with such rows keeps them (A13), any other rows are replaced. */
    fun chooseDaily() {
        val today = if (lineupEnabled()) LineupDraft.today(games.value) else LineupDraft(emptyList())
        edit(friend = { DuelDraft.daily(games.value) }, lineup = { rows, from ->
            val keep = from.preset == DuelPreset.Custom && rows.draft.isToday
            if (today.entries.isEmpty() || keep) rows.unpinned() else rows.unpinned().copy(draft = today)
        })
    }
    fun setCount(count: Int) = edit(friend = { it.withCount(count) }, self = { it.withCount(count) })
    fun toggle(game: DuelGame) = edit(friend = { it.toggled(game) }, self = { it.toggled(game.id) })
    fun setDeadline(hours: Int) = edit(friend = { it.withDeadline(hours) })
    fun setFriends(included: Boolean) = edit(friend = { it.withFriends(included) }, self = { it.withFriends(included) })

    /** Customize, with the lineup on, is a preset of its own: the duel is the lineup's puzzles. The rows are kept from before. */
    fun chooseCustom() = working.update { if (it.draft.preset == DuelPreset.Custom) it else it.withoutNotice().copy(draft = it.draft.customized()) }

    /** Applies [edit] to the lineup; it is a Customize edit, so it leaves Quick and Daily, and it clears a refusal. */
    fun editLineup(edit: (LineupEditor) -> LineupEditor) = working.update {
        val cleared = if (it.notice is DuelNotice.EntryUndrawable) it.withoutNotice() else it
        val next = edit(cleared.lineup)
        // Daily's rows can be reordered and removed without leaving Daily.
        if (next == cleared.lineup) it else cleared.copy(lineup = next, draft = if (it.draft.preset == DuelPreset.Daily) it.draft else it.draft.customized())
    }

    /**
     * Opens as a challenge: [challenge]'s friend is invited, and the duel is one puzzle of its game. The same challenge
     * again (the sheet recomposed, the screen turned) leaves the draft as the player has edited it.
     */
    fun prefill(challenge: DuelChallenge) = working.update {
        if (it.challenge == challenge) {
            it
        } else {
            it.copy(
                draft = DuelDraft.single(challenge.game), challenge = challenge, opponent = Opponent.Friend, notice = null,
                lineup = LineupEditor(LineupDraft(listOf(AnyGame(challenge.game)))),
            )
        }
    }

    /**
     * Applies the edit that belongs to the opponent on show; one the Past you form has no use for (Daily, deadline) is dropped.
     * A friend duel's edit also drops the puzzles a failed Create drew, since it may change what a draw gives.
     */
    private fun edit(
        friend: (DuelDraft) -> DuelDraft,
        self: (SelfDuelForm) -> SelfDuelForm = { it },
        lineup: (LineupEditor, DuelDraft) -> LineupEditor = { rows, _ -> rows.unpinned() },
    ) = working.update {
        if (selfActive(it)) {
            it.withoutNotice().copy(form = self(it.form))
        } else {
            it.withoutNotice().let { cleared -> cleared.copy(draft = friend(it.draft), lineup = lineup(cleared.lineup, it.draft)) }
        }
    }

    /** The sheet opened: counts the puzzles solved since it last did. */
    fun refreshSolved() = sheetOpens.update { it + 1 }

    /** Back to the Quick duel with nothing pending (no challenge either), as the sheet closes; a create still in flight keeps its place in [created] but not the sheet. */
    fun reset() = working.update {
        DuelWorking(DuelDraft.quick(games.value), form = SelfDuelForm.quick(games.value), generation = it.generation + 1, lineup = LineupEditor.opening(games.value))
    }

    /** Create duel, one at a time; a created duel stays `creating` until [reset]. A failure from before a [reset] is dropped; a created duel is not. */
    fun create() {
        val start = working.getAndUpdate { if (it.creating) it else it.withoutNotice().copy(creating = true) }
        if (start.creating) return
        if (selfActive(start)) return createSelf(start)
        if (start.draft.games.isEmpty()) return
        // Lineup on, Customize chosen: the lineup's puzzles in order, or the ones a failed try drew when this is a retry.
        val lineup = start.lineup.takeIf { lineupEnabled() && (start.draft.preset == DuelPreset.Custom || start.draft.showsRows(start.lineup)) }
        viewModelScope.launch {
            val outcome = createMatch(start.draft.toSettings(lineup?.entries), invitedUid = start.challenge?.friendUid) { legs ->
                if (lineup != null) working.update { if (it.generation == start.generation) it.copy(lineup = it.lineup.pin(legs)) else it }
            }
            working.update { work ->
                if (work.generation != start.generation) return@update work
                when (outcome) {
                    is CreateOutcome.Created -> work
                    CreateOutcome.SignedOut -> work.copy(creating = false, signInRequired = true)
                    CreateOutcome.Offline -> work.copy(creating = false, notice = DuelNotice.Offline)
                    CreateOutcome.DailyCapReached -> work.copy(creating = false, notice = DuelNotice.Busy)
                    CreateOutcome.NoPuzzles -> work.copy(creating = false, notice = DuelNotice.NoPuzzles)
                    is CreateOutcome.Failed -> work.copy(creating = false, notice = DuelNotice.Failed(outcome.message))
                    is CreateOutcome.EntryUndrawable -> work.copy(
                        creating = false,
                        notice = DuelNotice.EntryUndrawable(outcome.position, registry.displayName(outcome.gameId)),
                        lineup = work.lineup.refused(outcome.position),
                    )
                }
            }
            // The server write is done whatever the sheet did meanwhile, so its code must still reach the creator.
            if (outcome is CreateOutcome.Created) {
                createdCodes.send(CreatedDuel(outcome.code, outcome.legs.map { registry.displayName(it.ref.gameId) }, start.draft.deadlineHours))
            }
        }
    }

    /** Create against Past you: no account, no code to share; the new duel's code leaves through [opened]. */
    private fun createSelf(start: DuelWorking) {
        viewModelScope.launch {
            val outcome = selfStep.create(start.form, solved.value)
            working.update { work ->
                if (work.generation != start.generation) return@update work
                when (outcome) {
                    is CreateOutcome.Created -> work
                    null -> work.copy(creating = false)
                    CreateOutcome.NoPuzzles -> work.copy(creating = false, notice = DuelNotice.NoPuzzles)
                    else -> work.copy(creating = false, notice = DuelNotice.Failed(null))
                }
            }
            // The duel is stored whatever the sheet did meanwhile, so its code must still reach the creator.
            if (outcome is CreateOutcome.Created) openedCodes.send(outcome.code)
        }
    }

    /**
     * Sign in with Google: [activityContext] hosts the account picker and is not kept. A try under way is not
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
