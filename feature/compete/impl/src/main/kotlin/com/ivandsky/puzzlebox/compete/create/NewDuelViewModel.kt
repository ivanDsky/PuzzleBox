package com.ivandsky.puzzlebox.compete.create

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivandsky.puzzlebox.auth.GoogleIdTokenProvider
import com.ivandsky.puzzlebox.common.flags.ReleaseFlags
import com.ivandsky.puzzlebox.common.flags.isEnabled
import com.ivandsky.puzzlebox.compete.CompeteFlag
import com.ivandsky.puzzlebox.compete.create.lineup.LineupDraft
import com.ivandsky.puzzlebox.compete.create.lineup.LineupEditor
import com.ivandsky.puzzlebox.compete.domain.ComputeSolvedPool
import com.ivandsky.puzzlebox.compete.domain.CreateSelfDuel
import com.ivandsky.puzzlebox.compete.domain.lineup.ObserveLegTitles
import com.ivandsky.puzzlebox.data.calendar.GameCalendar
import com.ivandsky.puzzlebox.data.identity.IdentityService
import com.ivandsky.puzzlebox.data.repository.DailyRepository
import com.ivandsky.puzzlebox.domain.CreateMatch
import com.ivandsky.puzzlebox.domain.CreateOutcome
import com.ivandsky.puzzlebox.domain.ObserveTodaySet
import com.ivandsky.puzzlebox.domain.TodayStatus
import com.ivandsky.puzzlebox.domain.lineup.AnyGame
import com.ivandsky.puzzlebox.game.GameRegistry
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
 * opening starts from the Quick duel again. Customize edits a [LineupEditor] and Create sends its puzzles in order.
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
    private val legTitles: ObserveLegTitles,
    releaseFlags: ReleaseFlags,
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
            )
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** The new duel, once, after Create succeeded: a duel created as the sheet closed waits here for the next opening. */
    val created: Flow<CreatedDuel> = createdCodes.receiveAsFlow()

    /** The games [created]'s invite text lists; see [inviteGames]. */
    suspend fun inviteGames(created: CreatedDuel): List<String> = created.inviteGames(legTitles)

    /** The signed-in player's name for the invite text; null while signed out. */
    fun inviter(): String? = identity.current.value?.displayName

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

    private val builder = NewDuelStateBuilder(clock, zone, locale, pastYouPhoto = releaseFlags.isEnabled(CompeteFlag.PastYouPhoto))

    val state: StateFlow<NewDuelState> = combine(working, games, identity.current, solved, builder::build)
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            builder.build(working.value, emptyList(), identity.current.value, solved.value),
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

    private fun selfActive(work: DuelWorking) = work.opponent == Opponent.PastYou && work.challenge == null

    /** Past you or a friend: the choice is kept apart, so each keeps its own draft while the other is shown. */
    fun chooseOpponent(opponent: Opponent) = working.update { it.withoutNotice().copy(opponent = opponent) }

    fun chooseQuick() = edit(friend = { DuelDraft.quick(games.value) }, self = { SelfDuelForm.quick(games.value) })

    /** Daily shows today's puzzles as rows; coming from Customize with such rows keeps them (A13), any other rows are replaced. */
    fun chooseDaily() {
        edit(friend = { DuelDraft.daily(games.value) }, lineup = { rows, from ->
            val keep = from.preset == DuelPreset.Custom && rows.draft.isToday(games.value)
            val today = if (keep) null else LineupDraft.today(games.value)
            if (today == null || today.entries.isEmpty()) rows.unpinned() else rows.unpinned().copy(draft = today)
        })
    }
    fun toggle(game: DuelGame) = edit(friend = { it.toggled(game) })
    fun setDeadline(hours: Int) = edit(friend = { it.withDeadline(hours) })
    fun setFriends(included: Boolean) = edit(friend = { it.withFriends(included) }, self = { it.withFriends(included) })

    /**
     * Customize is a preset of its own: the duel is the lineup's puzzles. The rows are kept from before.
     * Past you's lineup is seeded with Any rows over what the player has solved once the counts are read, and only while it is empty; one with rows is never reseeded.
     */
    fun chooseCustom() = working.update { work ->
        if (selfActive(work)) {
            val opening = solved.value?.takeIf { work.selfLineup.draft.entries.isEmpty() }?.let { LineupDraft.openingSolved(games.value, it.byGame) }
            val next = work.copy(form = work.form.customized(), selfLineup = if (opening == null) work.selfLineup else work.selfLineup.copy(draft = opening))
            if (next == work) work else next.withoutNotice()
        } else if (work.draft.preset == DuelPreset.Custom) {
            work
        } else {
            work.withoutNotice().copy(draft = work.draft.customized())
        }
    }

    /** Applies [edit] to the lineup on show (Past you's, or the friend duel's); it is a Customize edit, so it leaves Quick and Daily, and it clears a refusal. */
    fun editLineup(edit: (LineupEditor) -> LineupEditor) = working.update {
        val cleared = if (it.notice is DuelNotice.EntryUndrawable || it.notice is DuelNotice.EntryUnavailable) it.withoutNotice() else it
        if (selfActive(it)) {
            val next = edit(cleared.selfLineup)
            return@update if (next == cleared.selfLineup) it else cleared.copy(selfLineup = next, form = it.form.customized())
        }
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
        // Customize chosen, or Daily showing its rows: the lineup's puzzles in order, or the ones a failed try drew when this is a retry.
        val lineup = start.lineup.takeIf { start.draft.preset == DuelPreset.Custom || start.draft.showsRows(start.lineup, games.value) }
        viewModelScope.launch {
            val outcome = createMatch(start.draft.toSettings(lineup?.entries), invitedUid = start.challenge?.friendUid) { legs ->
                if (lineup != null) working.update { if (it.generation == start.generation) it.copy(lineup = it.lineup.pin(legs)) else it }
            }
            working.update { work -> settled(work, start, outcome) { it.after(outcome, registry::displayName) } }
            // The server write is done whatever the sheet did meanwhile, so its code must still reach the creator.
            if (outcome is CreateOutcome.Created) {
                val games = outcome.legs.map { registry.displayName(it.ref.gameId) }
                createdCodes.send(CreatedDuel(outcome.code, games, start.draft.deadlineHours, outcome.legs.map { it.ref }))
            }
        }
    }

    /** Create against Past you: no account, no code to share; the new duel's code leaves through [opened]. */
    private fun createSelf(start: DuelWorking) {
        viewModelScope.launch {
            val lineup = start.selfLineup.entries.takeIf { start.form.preset == DuelPreset.Custom }
            val outcome = selfStep.create(start.form, solved.value, lineup)
            working.update { work -> settled(work, start, outcome) { it.afterSelf(outcome, registry::displayName) } }
            // The duel is stored whatever the sheet did meanwhile, so its code must still reach the creator.
            if (outcome is CreateOutcome.Created) openedCodes.send(outcome.code)
        }
    }

    /** [onShow] for a Create's outcome on the pane it started on; dropped after a [reset], and after a switch to the other pane, whose notice and rows it must not touch. */
    private fun settled(work: DuelWorking, start: DuelWorking, outcome: CreateOutcome?, onShow: (DuelWorking) -> DuelWorking) = when {
        work.generation != start.generation -> work
        selfActive(work) != selfActive(start) -> work.afterAway(outcome)
        else -> onShow(work)
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
