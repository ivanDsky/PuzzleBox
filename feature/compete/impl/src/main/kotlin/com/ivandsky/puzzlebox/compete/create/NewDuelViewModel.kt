package com.ivandsky.puzzlebox.compete.create

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivandsky.puzzlebox.auth.GoogleIdTokenProvider
import com.ivandsky.puzzlebox.data.calendar.GameCalendar
import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.data.identity.IdentityService
import com.ivandsky.puzzlebox.data.match.Match
import com.ivandsky.puzzlebox.data.repository.DailyRepository
import com.ivandsky.puzzlebox.domain.CreateMatch
import com.ivandsky.puzzlebox.domain.CreateOutcome
import com.ivandsky.puzzlebox.domain.DuelSettings
import com.ivandsky.puzzlebox.domain.ObserveTodaySet
import com.ivandsky.puzzlebox.domain.TodayStatus
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.ShareCode
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
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

/** Which preset the sheet shows as chosen; any edit to a Quick draft leaves it Custom. */
internal enum class DuelPreset { Quick, Daily, Custom }

/**
 * A game the sheet can offer: its [name], today's puzzle [todayNumber] (null when its source numbers none),
 * whether the player already [solvedToday] and whether it ranks [hintsFirst].
 */
internal data class DuelGame(
    val id: GameId,
    val name: String,
    val todayNumber: Int?,
    val solvedToday: Boolean,
    val hintsFirst: Boolean,
)

/**
 * What the sheet has chosen so far. [todayOnly] is the Daily duel's rule; everything else is the customize panel.
 * Pure: [toSettings] and [summary] hold the mapping and the footer text, and every edit returns a new draft.
 */
internal data class DuelDraft(
    val preset: DuelPreset,
    val count: Int,
    val games: Set<GameId>,
    val todayOnly: Boolean,
    val deadlineHours: Int,
    val includeFriends: Boolean,
) {

    /** The request [CreateMatch] takes; this sheet never pastes codes (Enter code does). */
    fun toSettings() = DuelSettings(
        count = count,
        games = games,
        todayOnly = todayOnly,
        deadlineHours = deadlineHours,
        includeFriends = includeFriends,
        pastedCodes = emptyList(),
    )

    /** "Best of 3 · mixed past dailies · ends Sun 18:00", plus " · incl. friends' puzzles" when friends are in. */
    fun summary(now: Instant, zone: ZoneId, available: List<DuelGame>, locale: Locale): String {
        val chosen = available.filter { it.id in games }.map { it.name }
        val gamesText = when {
            todayOnly -> "today's ${chosen.joinToString(", ")}"
            chosen.size == available.size -> "mixed past dailies"
            else -> chosen.joinToString(", ")
        }
        val ends = DateTimeFormatter.ofPattern("EEE HH:mm", locale).format(now.plusSeconds(deadlineHours * SECONDS_PER_HOUR).atZone(zone))
        return buildString {
            append("Best of $count · $gamesText · ends $ends")
            if (includeFriends) append(" · incl. friends' puzzles")
        }
    }

    fun withCount(value: Int): DuelDraft = edited().copy(count = value.coerceIn(MIN_COUNT, Match.MAX_LEGS))
    fun withDeadline(hours: Int): DuelDraft = edited().copy(deadlineHours = hours)
    fun withFriends(included: Boolean): DuelDraft = edited().copy(includeFriends = included)

    /** Flips [game]; the last chosen game stays, and a solved game cannot join a today-only duel. A today-only duel plays each chosen game once. */
    fun toggled(game: DuelGame): DuelDraft {
        if (todayOnly && game.solvedToday) return this
        val next = if (game.id in games) games - game.id else games + game.id
        if (next.isEmpty()) return this
        val rounds = if (todayOnly) next.size.coerceIn(MIN_COUNT, Match.MAX_LEGS) else count
        return edited().copy(games = next, count = rounds)
    }

    /** Quick leaves its preset on the first edit; the Daily duel stays today-only whatever else changes. */
    private fun edited(): DuelDraft = if (preset == DuelPreset.Quick) copy(preset = DuelPreset.Custom) else this

    companion object {
        const val MIN_COUNT = 1
        private const val QUICK_COUNT = 3
        private const val SECONDS_PER_HOUR = 3_600L

        /** Best of 3, mixed past dailies of every game, 24 h. */
        fun quick(available: List<DuelGame>) = DuelDraft(
            preset = DuelPreset.Quick,
            count = QUICK_COUNT,
            games = available.mapTo(LinkedHashSet()) { it.id },
            todayOnly = false,
            deadlineHours = Match.DEADLINE_CHOICES.first(),
            includeFriends = false,
        )

        /** Today's puzzle of each game not solved yet, 24 h; one round per game. */
        fun daily(available: List<DuelGame>): DuelDraft {
            val open = available.filterNot { it.solvedToday }.mapTo(LinkedHashSet()) { it.id }
            return quick(available).copy(
                preset = DuelPreset.Daily,
                count = open.size.coerceIn(MIN_COUNT, Match.MAX_LEGS),
                games = open,
                todayOnly = true,
            )
        }
    }
}

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
    registry: GameRegistry,
    private val clock: () -> Instant,
) : ViewModel() {

    private data class Working(
        val draft: DuelDraft,
        val creating: Boolean = false,
        val notice: DuelNotice? = null,
        val signInRequired: Boolean = false,
        val signIn: DuelSignIn = DuelSignIn.Idle,
        val generation: Int = 0,
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

    /** The new duel's code and the names of the games its puzzles are from, in puzzle order, once, after Create succeeded. */
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
        summary = work.draft.summary(clock(), ZoneId.systemDefault(), available, Locale.getDefault()),
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

    private fun edit(change: (DuelDraft) -> DuelDraft) = working.update { it.copy(draft = change(it.draft), notice = null) }

    /** Back to the Quick duel with nothing pending, as the sheet closes; a create still in flight is dropped. */
    fun reset() = working.update { Working(DuelDraft.quick(games.value), generation = it.generation + 1) }

    /** Create duel, one at a time; a created duel stays `creating` until [reset]. An outcome from before a [reset] is dropped. */
    fun create() {
        val start = working.getAndUpdate { if (it.creating) it else it.copy(creating = true, notice = null) }
        if (start.creating || start.draft.games.isEmpty()) return
        viewModelScope.launch {
            val outcome = createMatch(start.draft.toSettings())
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
                val names = outcome.legs.map { it.ref.gameId }.distinct().mapNotNull { id -> games.value.firstOrNull { it.id == id }?.name }
                createdCodes.send(outcome.code to names)
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
