package com.ivandsky.puzzlebox.play

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivandsky.puzzlebox.common.flags.ReleaseFlags
import com.ivandsky.puzzlebox.common.flags.isEnabled
import com.ivandsky.puzzlebox.data.calendar.GameCalendar
import com.ivandsky.puzzlebox.data.repository.CompletionRepository
import com.ivandsky.puzzlebox.data.repository.DailyRepository
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
import com.ivandsky.puzzlebox.play.domain.linkedin.ObserveLinkedInPost
import com.ivandsky.puzzlebox.play.domain.linkedin.SubmitLinkedInResult
import com.ivandsky.puzzlebox.domain.nextthing.ObserveResultNextThing
import com.ivandsky.puzzlebox.domain.nextUnsolved
import com.ivandsky.puzzlebox.game.BestScore
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.game.PuzzleRef
import com.ivandsky.puzzlebox.gameui.GameBrand
import com.ivandsky.puzzlebox.gameui.GamePlayRegistry
import com.ivandsky.puzzlebox.gameui.GamePlayUi
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.PlaySlot
import com.ivandsky.puzzlebox.model.PuzzleKey
import com.ivandsky.puzzlebox.play.api.ResultKey
import com.ivandsky.puzzlebox.play.friend.FriendPuzzleBoard
import com.ivandsky.puzzlebox.resultui.LinkedInPostRowState
import com.ivandsky.puzzlebox.resultui.ResultPrimaryAction
import com.ivandsky.puzzlebox.resultui.SamePuzzleSolves
import com.ivandsky.puzzlebox.resultui.replay.ReplaySectionState
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.runningFold
import kotlinx.coroutines.flow.stateIn

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
 * result (a play again) offers Done or, for a past day solved for the first time, NextDay instead ([ResultArchiveNext]
 * over [daily]), null until its history row lands. The read flows run only while the screen is observed (plus a 5 s
 * grace), so a day change is re-read.
 *
 * [heroInsights], [heroRank] and [playAgain] feed the hero page. A day's result shows the same side card whether
 * it is today's or a play again: the streak, the time spread and the rank.
 *
 * The Watch replay row is [ResultReplayRow]'s; a game with no replay board skips the load.
 *
 * Today's solve asks [nextThing] once for its moments, next thing and Challenge friend ([nextFacts], see
 * [resultNextFacts]) and [primary] is that next thing; a past day's result never builds [nextThing].
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
    nextThing: Lazy<ObserveResultNextThing>,
    daily: DailyRepository,
) : ViewModel() {
    private val slot = PlaySlot.Daily(GameId(key.gameId), LocalDate.parse(key.day))
    private val solve = ActivePlay.Solve(key.timeMs, key.hintsUsed)
    private val live: ActivePlay? = store.peek(slot)
    private val liveHoldsSolve = live != null && live.record.value.solve == solve
    private val names: Map<GameId, String> = store.playable.toMap()
    private val today: Boolean = calendar.today(slot.gameId) == slot.day
    private val replayRow = ResultReplayRow(completions, loadReplay, solve, live.takeIf { liveHoldsSolve })
    private val columns = releaseFlags?.isEnabled(PlayFlag.ResultHeroPolish) == true
    private val share = ResultShare(slot)
    private val samePuzzleRows = ResultSamePuzzle(completions, solve, live.takeIf { liveHoldsSolve }, games.rankOrderOf(slot.gameId))

    val brand: GameBrand = ui.brandOf(slot.gameId)

    /** The game's play UI, which owns its replay board; null for a game this build does not ship. */
    val playUi: GamePlayUi? = ui[slot.gameId]

    /** This solve's record facts, folded eagerly so a later play again never erases them. */
    private val record: StateFlow<ResultUiState> = ResultUiState(
        game = games.displayName(slot.gameId),
    ).let { blank ->
        if (live != null) {
            val first = blank.withRecord(live.record.value, solve)
            live.record.runningFold(first) { last, rec -> last.withRecord(rec, solve) }.stateIn(viewModelScope, SharingStarted.Eagerly, first)
        } else {
            MutableStateFlow(blank)
        }
    }

    private val playableToday: Flow<List<TodayEntry>> =
        todaySet().map { entries -> entries.filter { it.game in names } }

    private val dayRow = history().map { days -> days.firstOrNull { it.gameId == slot.gameId && it.day == slot.day } }

    /** The slot's puzzle ref once the history row gives its edition number; null before, and for a practice puzzle's missing row. */
    private val refOfDay: Flow<PuzzleRef?> = dayRow
        .map { day -> day?.let { games[slot.gameId]?.refOf?.invoke(PuzzleKey.Daily(slot.day, it.puzzleNumber)) } }
        .distinctUntilChanged()

    /** Needs the edition number, which only the history row has, so it waits for that row. */
    private val replay: Flow<ResultReplay> = refOfDay
        .flatMapLatest { ref -> if (ref == null || playUi?.replayBoard == null) flowOf(ResultReplay(ReplaySectionState.Hidden)) else replayRow.of(ref) }

    /** The player's earlier solves of this puzzle; None for a practice puzzle. With Result hero polish on they are drawn as two columns. */
    private val samePuzzle: Flow<SamePuzzleSolves> =
        refOfDay.flatMapLatest { ref -> ref?.let(samePuzzleRows::of) ?: flowOf(SamePuzzleSolves.None) }
            .map { it.copy(columns = columns) }

    val state: StateFlow<ResultUiState> = combine(
        record,
        dayRow,
        playableToday,
        replay,
        samePuzzle,
    ) { facts, day, set, replay, samePuzzle ->
        val practice = day != null && day.puzzleNumber == null
        facts.copy(
            puzzleNumber = day?.puzzleNumber,
            practice = practice,
            doneToday = set.count { it.status is TodayStatus.Solved || (today && it.game == slot.gameId) },
            total = set.size,
            rolloverAt = rollover(),
            replay = replay.section,
            replayLatest = replay.latest,
            samePuzzle = samePuzzle,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), record.value)

    /** A practice puzzle's next step: the next unsolved game the store can play, or AllDone. */
    private val nextUnsolved: Flow<ResultPrimaryAction> = playableToday.map { set ->
        set.nextUnsolved(after = slot.gameId)
            ?.let { next -> ResultPrimaryAction.Next(next, set.first { it.game == next }.displayName) }
            ?: ResultPrimaryAction.AllDone
    }

    /** Today's solve only: its moments, next thing and Challenge friend, asked once, and [nextThing] is built only when the page first observes it. A past day never builds it. */
    private val moments: Flow<ResultNextFacts>? = if (today) {
        flow { emitAll(resultNextFacts(nextThing.value, slot, solve, live.takeIf { liveHoldsSolve }, dayRow, nextUnsolved, games::displayName)) }
    } else {
        null
    }

    /** The moment card, the next thing and the Challenge row's friend; empty for a past day's result. */
    internal val nextFacts: StateFlow<ResultNextFacts> =
        moments?.stateIn(viewModelScope, SharingStarted.Lazily, ResultNextFacts()) ?: MutableStateFlow(ResultNextFacts())

    val primary: StateFlow<ResultPrimaryAction?> = if (moments != null) {
        nextFacts.map { it.primary }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)
    } else {
        // A past day's first solve offers the same game on its next unsolved day, else Done; null until the day's row lands.
        val next = ResultArchiveNext(daily, completions, calendar, samePuzzleRows, slot, games.displayName(slot.gameId))
        refOfDay.filterNotNull().flatMapLatest(next::of).distinctUntilChanged()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)
    }

    /** True for a past day's result: a play again counts nothing. */
    val playAgain: Boolean = !today

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

    /** Result hero polish: the signed-out hero carries the sign-in invitation under the time. */
    val heroSignInLine: Boolean = releaseFlags?.isEnabled(PlayFlag.ResultHeroPolish) == true

    private val post = ResultPostButton(viewModelScope, observePost, submitPost, slot.gameId, slot.day)

    /** The Post to LinkedIn button, null for no button; see [ResultPostButton]. */
    val linkedInPost: StateFlow<LinkedInPostRowState?> get() = post.state

    /**
     * The time the Post to LinkedIn row names: the day's record row, the solve a post sends and LinkedIn holds,
     * never this page's own time, which after a play again is another solve. Null while the day has no record row.
     */
    val linkedInPostTimeMs: StateFlow<Long?> get() = post.timeMs

    /** The button was tapped. */
    fun postToLinkedIn() = post.tap()

    private val signInRow = ResultSignIn(viewModelScope, board)

    /** Why the last sign-in did not finish, in words for the rank row; null while it is idle or under way. */
    val signInNote: StateFlow<String?> get() = signInRow.note

    /** The Share sheet's text for [state]; it carries the strip and the link, see [ResultShare]. */
    fun shareText(state: ResultUiState): String = share.textOf(state, solve.timeMs, solve.hints)

    /** The rank row's Google button: [activityContext] hosts Google's account picker, and is not kept. */
    fun signIn(activityContext: Context) = signInRow.tap(activityContext)

    fun retryRecordWrite() {
        live?.takeIf { it.record.value.solve == solve }?.retryRecordWrite()
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
