package com.ivandsky.puzzlebox.play

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivandsky.puzzlebox.common.flags.ReleaseFlag
import com.ivandsky.puzzlebox.common.flags.ReleaseFlags
import com.ivandsky.puzzlebox.common.flags.isEnabled
import com.ivandsky.puzzlebox.data.calendar.GameCalendar
import com.ivandsky.puzzlebox.data.repository.CompletionRepository
import com.ivandsky.puzzlebox.domain.GetNextRollover
import com.ivandsky.puzzlebox.domain.LoadSolveReplay
import com.ivandsky.puzzlebox.domain.ObserveGameHistory
import com.ivandsky.puzzlebox.domain.ObserveResultInsights
import com.ivandsky.puzzlebox.domain.ObserveResultRank
import com.ivandsky.puzzlebox.domain.ObserveTodaySet
import com.ivandsky.puzzlebox.domain.ResultInsights
import com.ivandsky.puzzlebox.domain.ResultRank
import com.ivandsky.puzzlebox.domain.TodayEntry
import com.ivandsky.puzzlebox.domain.TodayStatus
import com.ivandsky.puzzlebox.domain.linkedin.ObserveLinkedInDay
import com.ivandsky.puzzlebox.domain.linkedin.ObserveLinkedInPost
import com.ivandsky.puzzlebox.domain.linkedin.SubmitLinkedInResult
import com.ivandsky.puzzlebox.domain.nextthing.ObserveResultNextThing
import com.ivandsky.puzzlebox.domain.nextUnsolved
import com.ivandsky.puzzlebox.game.BestScore
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.game.VariantId
import com.ivandsky.puzzlebox.gameui.GameBrand
import com.ivandsky.puzzlebox.gameui.GamePlayRegistry
import com.ivandsky.puzzlebox.gameui.GamePlayUi
import com.ivandsky.puzzlebox.gameui.LinkedInPostRowState
import com.ivandsky.puzzlebox.gameui.RecordVerdict
import com.ivandsky.puzzlebox.gameui.ResultPrimaryAction
import com.ivandsky.puzzlebox.gameui.replay.ReplaySectionState
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.PlaySlot
import com.ivandsky.puzzlebox.model.PuzzleKey
import com.ivandsky.puzzlebox.play.api.ResultKey
import com.ivandsky.puzzlebox.play.friend.FriendPuzzleBoard
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.runningFold
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The result route's live facts. [verdict] is the A10 verdict for THIS play — null while
 * the write is in flight/unknown, or when no live play holds this solve; [previousBest]
 * is the game's best before this play, ranked by [rankOrder]; [writeFailed] (the retry
 * affordance) is only ever true for a live play holding this solve.
 *
 * [game] names the game; [puzzleNumber] is the slot's edition number, and [practice] is
 * true once the history shows the slot has none (A22: it never ranks or counts).
 * [doneToday] of [total] is today's playable set solved, this game counted the moment it
 * is solved; [rolloverAt] is when the next puzzles land. [replay] is Ready once this solve is stored with a replay,
 * Hidden before (a pending or failed write), or when there is none (A13).
 */
data class ResultUiState(
    val verdict: RecordVerdict? = null,
    val previousBest: BestScore? = null,
    val rankOrder: RankOrder = RankOrder.TimeThenHints,
    val writeFailed: Boolean = false,
    val game: String = "",
    val puzzleNumber: Int? = null,
    val practice: Boolean = false,
    val doneToday: Int? = null,
    val total: Int? = null,
    val rolloverAt: Instant? = null,
    val replay: ReplaySectionState = ReplaySectionState.Hidden,
)

/**
 * The result route's state holder for [key]'s slot. The slot's live play
 * ([PlaySessionStore.peek]) speaks for this result only while it holds THIS solve — won,
 * with the key's time and hints: then the verdict, previous best and record-write retry
 * come from it. Once another stack plays again or re-solves the slot, this entry keeps the
 * verdict it already saw and hides retry (A9, F2). Without a live play holding the solve
 * at entry (process death) the verdict is unknown (A15, N20): the session-start baseline
 * the verdict needs died with the play.
 *
 * Everything else is read, so it holds after process death too: the edition number from
 * [history] (the same cached row the play's identity uses), the day's progress from
 * [todaySet] and the next rollover from [rollover]. [brand] is the game's registered brand.
 *
 * [primary] (A7, SC10): on today's slot it is Next — [todaySet]'s next unsolved game after
 * this one that the [store] can play — or AllDone when none is left, and null until the set
 * first lands, so the screen never shows a Done that turns into Next. Any other day's
 * result (a play again) offers Done from the first frame. The read flows run only while the
 * screen is observed (plus a 5 s grace), so a day change is re-read.
 *
 * [heroInsights], [heroRank] and [playAgain] feed the hero page. A day's result shows the same side card whether
 * it is today's or a play again: the streak, the time spread and the rank.
 *
 * The Watch replay row is [ResultReplayRow]'s; a game with no replay board skips the load.
 *
 * With Result moments on ([releaseFlags]), today's solve asks [nextThing] once for its moments, next thing and
 * Challenge friend ([nextFacts], see [resultNextFacts]) and [primary] is that next thing; off, [nextThing] is never
 * built and everything above holds as written. [celebrating] is true for the one Result its board handed the solve to
 * ([CelebrationOnResult]).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ResultViewModel internal constructor(
    key: ResultKey,
    store: PlaySessionStore,
    todaySet: ObserveTodaySet,
    calendar: GameCalendar,
    history: ObserveGameHistory,
    rollover: GetNextRollover,
    ui: GamePlayRegistry,
    games: GameRegistry,
    insights: ObserveResultInsights,
    rank: ObserveResultRank,
    linkedIn: ObserveLinkedInDay?,
    private val board: FriendPuzzleBoard,
    completions: CompletionRepository,
    loadReplay: LoadSolveReplay,
    releaseFlags: ReleaseFlags? = null,
    observePost: ObserveLinkedInPost? = null,
    submitPost: SubmitLinkedInResult? = null,
    nextThing: Lazy<ObserveResultNextThing>? = null,
    celebration: CelebrationOnResult? = null,
) : ViewModel() {
    private val slot = PlaySlot.Daily(GameId(key.gameId), LocalDate.parse(key.day))
    private val solve = ActivePlay.Solve(key.timeMs, key.hintsUsed)
    private val live: ActivePlay? = store.peek(slot)
    private val liveHoldsSolve = live != null && live.record.value.solve == solve
    private val names: Map<GameId, String> = store.playable.toMap()
    private val today: Boolean = calendar.today(slot.gameId) == slot.day
    private val replayRow = ResultReplayRow(completions, loadReplay, solve, live.takeIf { liveHoldsSolve })

    val brand: GameBrand = ui.brandOf(slot.gameId)

    /** The game's play UI, which owns its replay board; null for a game this build does not ship. */
    val playUi: GamePlayUi? = ui[slot.gameId]

    /** This solve's record facts, folded eagerly so a later play again never erases them. */
    private val record: StateFlow<ResultUiState> = ResultUiState(game = games.displayName(slot.gameId)).let { blank ->
        if (live != null) {
            val first = resultOf(blank, live.record.value)
            live.record.runningFold(first, ::resultOf).stateIn(viewModelScope, SharingStarted.Eagerly, first)
        } else {
            MutableStateFlow(blank)
        }
    }

    private val playableToday: Flow<List<TodayEntry>> =
        todaySet().map { entries -> entries.filter { it.game in names } }

    private val dayRow = history().map { days -> days.firstOrNull { it.gameId == slot.gameId && it.day == slot.day } }

    /** Needs the edition number, which only the history row has, so it waits for that row. */
    private val replay: Flow<ReplaySectionState> = dayRow
        .map { day -> day?.let { games[slot.gameId]?.refOf?.invoke(PuzzleKey.Daily(slot.day, it.puzzleNumber)) } }
        .distinctUntilChanged()
        .flatMapLatest { ref -> if (ref == null || playUi?.replayBoard == null) flowOf(ReplaySectionState.Hidden) else replayRow.of(ref) }

    val state: StateFlow<ResultUiState> = combine(
        record,
        dayRow,
        playableToday,
        replay,
    ) { facts, day, set, replay ->
        val practice = day != null && day.puzzleNumber == null
        facts.copy(
            puzzleNumber = day?.puzzleNumber,
            practice = practice,
            doneToday = set.count { it.status is TodayStatus.Solved || (today && it.game == slot.gameId) },
            total = set.size,
            rolloverAt = rollover(),
            replay = replay,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), record.value)

    /** Today's rule without the moments: the next unsolved game the store can play, or AllDone. */
    private val nextUnsolved: Flow<ResultPrimaryAction> = playableToday.map { set ->
        set.nextUnsolved(after = slot.gameId)
            ?.let { next -> ResultPrimaryAction.Next(next, set.first { it.game == next }.displayName) }
            ?: ResultPrimaryAction.AllDone
    }

    /** Null unless Result moments are on and this is today's solve: then nothing of the next thing is built or read. */
    private val moments: Flow<ResultNextFacts>? =
        nextThing?.takeIf { today && releaseFlags?.isEnabled(ReleaseFlag.ResultMoments) == true }?.let { observe ->
            resultNextFacts(observe.value, slot, solve, live.takeIf { liveHoldsSolve }, dayRow, nextUnsolved, games::displayName)
        }

    /** The moment card, the next thing and the Challenge row's friend; empty for good with the moments off. */
    internal val nextFacts: StateFlow<ResultNextFacts> =
        moments?.stateIn(viewModelScope, SharingStarted.Lazily, ResultNextFacts()) ?: MutableStateFlow(ResultNextFacts())

    val primary: StateFlow<ResultPrimaryAction?> = when {
        !today -> MutableStateFlow(ResultPrimaryAction.Done)
        moments != null -> nextFacts.map { it.primary }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)
        else -> nextUnsolved.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)
    }

    /** True when this page opened under the solve celebration: it composes finished, with no entrance to play. */
    internal val celebrated: Boolean = celebration?.take(slot, solve) == true

    private val _celebrating = MutableStateFlow(celebrated)

    /** True while the solve celebration plays over the page: only for a solve its board just handed off. */
    internal val celebrating: StateFlow<Boolean> = _celebrating.asStateFlow()

    /** The celebration ended, by its timer or a tap. */
    internal fun celebrationDone() {
        _celebrating.value = false
    }

    /** True for a past day's result: a play again counts nothing. */
    val playAgain: Boolean = !today

    /** The size this solve was played at, read once from the stored puzzle; null until read, or when none is stored (the game's default size). */
    val playedVariant: StateFlow<VariantId?> = flow { emit(store.playedVariant(slot)) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /**
     * The hero page's streak, week and time spread; null until read, and for a practice puzzle.
     * Today's solve counts toward the streak and week; a play again's does not, so a play again shows the
     * player's current streak and week as the history stands, with no freeze moment.
     */
    val heroInsights: StateFlow<ResultInsights?> =
        insights(slot.gameId, slot.day, BestScore(key.timeMs, key.hintsUsed), counted = today)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    /**
     * The hero page's rank on the slot's day's board: today's, or for a play again the replayed past day's. A
     * play again's rank is the player's counting solve of that day, not this play again's time, and stays
     * [ResultRank.Hidden] while the player is not on that day's board. With [linkedIn] on, the day's connections
     * already on the phone fold into it; the day is only observed here, never fetched.
     */
    val heroRank: StateFlow<ResultRank> =
        rank(slot.gameId, slot.day, connections = linkedIn?.invoke(slot.gameId, slot.day))
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), ResultRank.Hidden)

    private val post = ResultPostButton(viewModelScope, releaseFlags, observePost, submitPost, slot.gameId, slot.day)

    /** The Post to LinkedIn button, null for no button; see [ResultPostButton]. */
    val linkedInPost: StateFlow<LinkedInPostRowState?> get() = post.state

    /** The button was tapped. */
    fun postToLinkedIn() = post.tap()

    /** The rank row's Google button: [activityContext] hosts Google's account picker, and is not kept. */
    fun signIn(activityContext: Context) {
        viewModelScope.launch { board.signIn(activityContext) }
    }

    fun retryRecordWrite() {
        live?.takeIf { it.record.value.solve == solve }?.retryRecordWrite()
    }

    /** This solve's facts while the play holds it; after a play again or another solve, the
     *  verdict this entry already saw stays (it was this solve's), and retry hides. */
    private fun resultOf(last: ResultUiState, record: ActivePlay.Record) =
        if (record.solve == solve) {
            last.copy(
                verdict = record.verdict,
                previousBest = record.previousBest,
                rankOrder = record.rankOrder,
                writeFailed = record.writeFailed,
            )
        } else {
            last.copy(writeFailed = false)
        }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
