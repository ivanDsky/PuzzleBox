package com.ivandsky.puzzlebox.play

import com.ivandsky.puzzlebox.common.dispatcher.DispatcherProvider
import com.ivandsky.puzzlebox.common.flags.ReleaseFlag
import com.ivandsky.puzzlebox.common.flags.ReleaseFlags
import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardEntry
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardRef
import com.ivandsky.puzzlebox.data.linkedin.LinkState
import com.ivandsky.puzzlebox.data.linkedin.LinkedInDayData
import com.ivandsky.puzzlebox.data.linkedin.LinkedInGhost
import com.ivandsky.puzzlebox.data.linkedin.LinkedInOwnState
import com.ivandsky.puzzlebox.data.linkedin.LinkedInPostState
import com.ivandsky.puzzlebox.data.linkedin.LinkedInPosts
import com.ivandsky.puzzlebox.data.repository.DailyEntry
import com.ivandsky.puzzlebox.data.match.LegRef
import com.ivandsky.puzzlebox.data.match.MatchDraft
import com.ivandsky.puzzlebox.data.match.MatchLeg
import com.ivandsky.puzzlebox.data.match.Player
import com.ivandsky.puzzlebox.data.visibility.VisibilityTier

import com.ivandsky.puzzlebox.domain.ComputeStreak
import com.ivandsky.puzzlebox.domain.FinishLeg
import com.ivandsky.puzzlebox.domain.GetNextRollover
import com.ivandsky.puzzlebox.domain.GetPuzzleDay
import com.ivandsky.puzzlebox.domain.LoadPastDay
import com.ivandsky.puzzlebox.domain.LoadSharedPuzzle
import com.ivandsky.puzzlebox.domain.ObserveGameHistory
import com.ivandsky.puzzlebox.domain.ObserveLeaderboard
import com.ivandsky.puzzlebox.domain.ObserveResultInsights
import com.ivandsky.puzzlebox.domain.ObserveResultRank
import com.ivandsky.puzzlebox.domain.ResultRank
import com.ivandsky.puzzlebox.domain.ObserveTodaySet
import com.ivandsky.puzzlebox.domain.SyncTodayPuzzles
import com.ivandsky.puzzlebox.domain.linkedin.ObserveLinkedInDay
import com.ivandsky.puzzlebox.domain.linkedin.ObserveLinkedInPost
import com.ivandsky.puzzlebox.domain.linkedin.SubmitLinkedInResult
import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.domain.moments.Moment
import com.ivandsky.puzzlebox.domain.nextthing.ObserveResultNextThing
import com.ivandsky.puzzlebox.domain.ranks.ObserveFriendUids
import com.ivandsky.puzzlebox.domain.ObserveFriendPuzzles
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.game.RecordStats
import com.ivandsky.puzzlebox.game.editor.Budget
import com.ivandsky.puzzlebox.gameui.GamePlayRegistry
import com.ivandsky.puzzlebox.gameui.LinkedInPostRowState
import com.ivandsky.puzzlebox.game.BestScore
import com.ivandsky.puzzlebox.gameui.RecordVerdict
import com.ivandsky.puzzlebox.gameui.board.DailyBoardLine
import com.ivandsky.puzzlebox.gameui.board.DailyBoardRowModel
import com.ivandsky.puzzlebox.gameui.board.GhostRowModel
import com.ivandsky.puzzlebox.gameui.board.rankWithGhosts
import com.ivandsky.puzzlebox.gameui.ResultPrimaryAction
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.PlaySlot
import com.ivandsky.puzzlebox.model.ShareCode
import com.ivandsky.puzzlebox.model.PuzzleKey
import com.ivandsky.puzzlebox.play.api.GameKey
import com.ivandsky.puzzlebox.play.api.ResultKey
import com.ivandsky.puzzlebox.play.friend.signedOutBoard
import com.ivandsky.puzzlebox.testing.calendar.FakeGameCalendar
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.flags.FakeReleaseFlags
import com.ivandsky.puzzlebox.testing.linkedin.FakeLinkedInDays
import com.ivandsky.puzzlebox.testing.linkedin.FakeLinkedInPosts
import com.ivandsky.puzzlebox.testing.repository.FakeCompletionRepository
import com.ivandsky.puzzlebox.testing.repository.FakeDailyRepository
import com.ivandsky.puzzlebox.testing.repository.FakeIdentityService
import com.ivandsky.puzzlebox.testing.repository.FakeLeaderboardRepository
import com.ivandsky.puzzlebox.testing.repository.FakeMatchRepository
import com.ivandsky.puzzlebox.testing.repository.FakeMatchResultOutbox
import com.ivandsky.puzzlebox.testing.repository.FakeReceivedPuzzleRepository
import com.ivandsky.puzzlebox.testing.repository.FakeSavedPlayRepository
import com.ivandsky.puzzlebox.testing.repository.FakeSharedPuzzleRepository
import com.ivandsky.puzzlebox.testing.rule.MainDispatcherRule
import com.ivandsky.puzzlebox.testing.repository.FakeVisibilityRepository
import kotlinx.coroutines.runBlocking
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * The result VM for a [ResultKey]'s slot: live (the slot's play in the store, holding
 * this solve) it shows the play's verdict, previous best and write retry; once the
 * live play plays again or re-solves it keeps the verdict it saw and hides retry; cold (after
 * process death) the verdict is unknown — the session-start baseline died with the play.
 * The read facts hold either way: the edition number (none = practice), today's progress
 * and the rollover. The primary action is Next (the next unsolved
 * playable daily) or AllDone on today's slot only.
 */
class ResultViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val alpha = TestGames.alpha
    private val beta = TestGames.beta
    private val gamma = GameId("gamma")
    private val day = LocalDate.parse("2026-10-31")
    private val key = ResultKey(alpha.value, day.toString(), FakeController.TIME_MS, 0)
    private val fakes = FakeGames(listOf(alpha, beta))
    private val daily = FakeDailyRepository().apply {
        setToday(alpha, TestGames.puzzle(alpha))
        setHistory(alpha, listOf(DailyEntry(alpha, day, 7, "alpha/v1", 0L)))
    }
    private val completions = FakeCompletionRepository()
    private val leaderboard = FakeLeaderboardRepository()
    private val linkedInDays = FakeLinkedInDays()
    private val linkedInPosts = FakeLinkedInPosts()
    private var observedDays = 0
    private val countedPosts = object : LinkedInPosts by linkedInPosts {
        override fun observe(game: GameId, day: LocalDate): Flow<LinkedInPostState> {
            observedDays++
            return linkedInPosts.observe(game, day)
        }
    }
    private val dispatcher = main.testDispatcher
    private val calendar = FakeGameCalendar(day)
    private val saved = FakeSavedPlayRepository()
    private val lane = WriteLane()
    private val store = store(fakes.games, fakes.registry)
    private val todaySet = todaySet(fakes.games)

    /** The puzzle day pinned to the slot's [day], so its record counts toward the streak. */
    private val clock = Clock.fixed(day.atTime(12, 0).toInstant(ZoneOffset.UTC), ZoneOffset.UTC)

    /** Collectors standing in for the screen: [ResultViewModel.primary] only runs while observed. */
    private val screen = CoroutineScope(dispatcher + Job())

    @After
    fun leaveScreen() {
        screen.cancel()
    }

    private fun todaySet(games: GameRegistry) =
        ObserveTodaySet(games, saved, completions, calendar, daily, SyncTodayPuzzles(games, daily, calendar))

    private fun store(games: GameRegistry, ui: GamePlayRegistry) = PlaySessionStore(
        games = games,
        ui = ui,
        opening = SlotOpening(
            daily, LoadPastDay(daily, games), saved, calendar, games, lane, FakeReceivedPuzzleRepository(),
            FakeMatchRepository(),
            LoadSharedPuzzle(games, FakeSharedPuzzleRepository(), FakeReceivedPuzzleRepository(), { Budget.Unlimited }, { Instant.EPOCH }),
            testDispatchers(dispatcher),
        ),
        writers = { SlotWriter(it, saved, lane, CoroutineScope(dispatcher)) },
        completions = completions,
        finishLeg = FinishLeg(FakeMatchResultOutbox(), FakeIdentityService()) { Instant.EPOCH },
        calendar = calendar,
        dispatchers = object : DispatcherProvider {
            override val main = dispatcher
            override val io = dispatcher
            override val default = dispatcher
        },
    )

    private fun vm(
        key: ResultKey = this.key,
        games: GameRegistry = fakes.games,
        ui: GamePlayRegistry = fakes.registry,
        store: PlaySessionStore = this.store,
        identity: Identity? = null,
        linkedIn: Boolean = false,
        flags: ReleaseFlags? = null,
        celebration: CelebrationOnResult? = null,
    ): ResultViewModel {
        val history = ObserveGameHistory(games, daily, completions)
        val observeBoard = ObserveLeaderboard(FakeIdentityService(initial = identity), leaderboard, games, calendar)
        return ResultViewModel(
            key = key,
            store = store,
            todaySet = if (games === fakes.games) todaySet else todaySet(games),
            calendar = calendar,
            history = history,
            rollover = GetNextRollover(games, clock),
            ui = ui,
            games = games,
            insights = ObserveResultInsights(games, history, ComputeStreak(GetPuzzleDay(games, clock)), GetPuzzleDay(games, clock)),
            rank = ObserveResultRank(observeBoard, games, friendDay = null), // no Friends/Global setting
            linkedIn = if (linkedIn) ObserveLinkedInDay(linkedInDays, games) else null,
            releaseFlags = flags,
            observePost = flags?.let { ObserveLinkedInPost(countedPosts) },
            submitPost = flags?.let { SubmitLinkedInResult(linkedInPosts) },
            board = signedOutBoard(),
            completions = completions,
            loadReplay = loadSolveReplay(games, daily, completions),
            nextThing = lazy { nextThing(games) },
            celebration = celebration,
        )
    }

    /** The hero flows run only while the page observes them. */
    private fun ResultViewModel.heroObserved() = apply {
        screen.launch { heroInsights.collect {} }
        screen.launch { heroRank.collect {} }
        dispatcher.scheduler.runCurrent()
    }

    /** Both flows run only while the screen observes them. */
    private fun ResultViewModel.observed() = apply {
        screen.launch { primary.collect {} }
        screen.launch { state.collect {} }
        dispatcher.scheduler.runCurrent()
    }

    private fun solveLive(): ActivePlay {
        val play = store.open(GameKey(alpha.value, day.toString()))!!
        dispatcher.scheduler.runCurrent()
        fakes[alpha].latest.win()
        dispatcher.scheduler.runCurrent()
        return play
    }

    private fun record(time: Long, hints: Int, date: LocalDate = day, game: GameId = alpha) =
        CompletionRecord(TestGames.Ref(game, PuzzleKey.Daily(date, 7)), time, hints, RecordStats(1, "{}"), isRecord = true)

    @Test
    fun `live result shows the play's verdict`() {
        solveLive()
        val state = vm().observed().state.value
        assertEquals(RecordVerdict.FirstSolve, state.verdict)
        assertNull(state.previousBest)
        assertFalse(state.writeFailed)
        assertEquals(fakes.games.displayName(alpha), state.game)
    }

    @Test
    fun `live result carries the best this solve beat`() {
        val slower = FakeController.TIME_MS + 10_000
        completions.setRecords(listOf(record(slower, 0, date = day.minusDays(1))))
        solveLive()
        val state = vm().observed().state.value
        assertEquals(RecordVerdict.NewBest, state.verdict)
        assertEquals(BestScore(slower, hints = 0), state.previousBest)
    }

    @Test
    fun `live result surfaces a failed write and retries it`() {
        completions.fail = true
        solveLive()
        val vm = vm().observed()
        assertTrue(vm.state.value.writeFailed)

        completions.fail = false
        vm.retryRecordWrite()
        dispatcher.scheduler.runCurrent()
        assertFalse(vm.state.value.writeFailed)
        assertEquals(RecordVerdict.FirstSolve, vm.state.value.verdict)
    }

    @Test
    fun `a live play holding another solve never speaks for this result`() {
        solveLive()
        val state = vm(key.copy(timeMs = FakeController.TIME_MS + 1)).observed().state.value
        assertNull(state.verdict)
        assertNull(state.previousBest)
        assertFalse(state.writeFailed)
    }

    @Test
    fun `a play again of the live play keeps this result's own verdict`() {
        val play = solveLive()
        val vm = vm().observed()
        assertEquals(RecordVerdict.FirstSolve, vm.state.value.verdict)

        play.playAgain()
        dispatcher.scheduler.runCurrent()
        // The play again's in-flight state (no verdict yet) is not this result's.
        assertNull(play.record.value.verdict)
        assertEquals(RecordVerdict.FirstSolve, vm.state.value.verdict)
        assertFalse(vm.state.value.writeFailed)
    }

    @Test
    fun `cold result claims no verdict even with a matching record`() {
        completions.setRecords(listOf(record(FakeController.TIME_MS, 0)))
        val state = vm().observed().state.value
        assertNull(state.verdict)
        assertNull(state.previousBest)
    }

    @Test
    fun `cold result hides the write retry`() {
        completions.fail = true
        val vm = vm().observed()
        vm.retryRecordWrite()
        dispatcher.scheduler.runCurrent()
        assertFalse(vm.state.value.writeFailed)
    }

    @Test
    fun `the edition number comes from the slot's history row`() {
        val state = vm().observed().state.value
        assertEquals(7, state.puzzleNumber)
        assertFalse(state.practice)
    }

    @Test
    fun `a practice puzzle is practice and has no edition number`() {
        daily.setHistory(alpha, listOf(DailyEntry(alpha, day, null, "alpha/v1", 0L)))
        val state = vm().observed().state.value
        assertTrue(state.practice)
        assertNull(state.puzzleNumber)
    }

    @Test
    fun `today's progress counts this solve before its write lands`() {
        completions.setRecords(listOf(record(FakeController.TIME_MS, 0, game = beta)))
        val state = vm().observed().state.value
        assertEquals(2, state.total)
        assertEquals(2, state.doneToday)
    }

    @Test
    fun `the next rollover is read`() {
        val state = vm().observed().state.value
        assertNotNull(state.rolloverAt)
        assertTrue(state.rolloverAt!! > clock.instant())
    }

    @Test
    fun `today's result offers the next unsolved game`() {
        val vm = vm().observed()
        assertEquals(ResultPrimaryAction.Next(beta, fakes.games.displayName(beta)), vm.primary.value)
    }

    @Test
    fun `today's result with every other game solved is all done`() {
        completions.setRecords(listOf(record(FakeController.TIME_MS, 0, game = beta)))
        val vm = vm().observed()
        assertEquals(ResultPrimaryAction.AllDone, vm.primary.value)
    }

    @Test
    fun `Next skips a registered game the store cannot play`() {
        // beta has a plugin but no play UI: the hub never lists it, so Next must not name it.
        val three = FakeGames(listOf(alpha, beta, gamma))
        val playable = GamePlayRegistry(listOf(three[alpha], three[gamma]))
        val vm = vm(games = three.games, ui = playable, store = store(three.games, playable)).observed()
        assertEquals(ResultPrimaryAction.Next(gamma, three.games.displayName(gamma)), vm.primary.value)
    }

    @Test
    fun `a past day's result offers done, never next`() {
        val vm = vm(key.copy(day = day.minusDays(1).toString())).observed()
        assertEquals(ResultPrimaryAction.Done, vm.primary.value)
    }

    @Test
    fun `a play again is a past day's result`() {
        assertFalse(vm().playAgain)
        assertTrue(vm(key.copy(day = day.minusDays(1).toString())).playAgain)
    }

    @Test
    fun `today's insights count this solve before its write lands`() {
        val insights = vm().heroObserved().heroInsights.value
        assertEquals(1, insights?.streak)
    }

    @Test
    fun `a past day's insights do not count the solve`() {
        val playAgain = key.copy(day = day.minusDays(1).toString())
        assertEquals(0, vm(playAgain).heroObserved().heroInsights.value?.streak)
    }

    @Test
    fun `a play again's streak is the current one, and its own day never fills today`() {
        val yesterday = day.minusDays(1)
        completions.setRecords(listOf(record(70_000L, 0, date = yesterday), record(80_000L, 0, date = day.minusDays(2), game = beta)))

        val insights = vm(key.copy(day = yesterday.toString())).heroObserved().heroInsights.value!!

        assertEquals(2, insights.streak)
        assertFalse(insights.week.last().solved)
        assertTrue(insights.week.last().today)
        assertNull(insights.moment)
    }

    @Test
    fun `a practice puzzle has no insights`() {
        daily.setHistory(alpha, listOf(DailyEntry(alpha, day, null, "alpha/v1", 0L)))
        assertNull(vm().heroObserved().heroInsights.value)
    }

    @Test
    fun `the rank reads this game's board for the slot's day`() {
        val board = LeaderboardRef(alpha.value, day)
        leaderboard.setEntries(board, listOf(entry("a", 30), entry(ME.uid, 40)))
        val vm = vm(identity = ME).heroObserved()
        assertEquals(ResultRank.Ranked(position = 2, count = 2, topPercent = 100), vm.heroRank.value)
    }

    private fun ghost(name: String, seconds: Int?) =
        LinkedInGhost("ref-$name", name, seconds?.let { it * 1_000L }, hintFree = true, flawless = false)

    /** The slot day's connections board as read from LinkedIn and held on the phone. */
    private fun cachedConnections(vararg ghosts: LinkedInGhost) = linkedInDays.emit(alpha, day, connections(*ghosts))

    private fun connections(vararg ghosts: LinkedInGhost) = LinkedInDayData(
        link = LinkState.Connected("me"),
        delta = 0,
        boardInWindow = true,
        inFlight = false,
        lastSync = null,
        own = LinkedInOwnState.Unknown,
        ghosts = ghosts.toList(),
        boardFetchedAtMs = 1L,
    )

    private fun playersAt(vararg seconds: Pair<String, Int>) =
        leaderboard.setEntries(LeaderboardRef(alpha.value, day), seconds.map { (uid, s) -> entry(uid, s) })

    @Test
    fun `cached connections give the rank a combined place and keep the PuzzleBox rank`() {
        playersAt("a" to 30, ME.uid to 40, "b" to 50)
        cachedConnections(ghost("Ada", 20), ghost("Bo", 35), ghost("Cy", 45))

        val rank = vm(identity = ME, linkedIn = true).heroObserved().heroRank.value

        assertEquals(
            ResultRank.Ranked(
                position = 2, count = 3, topPercent = 67,
                withConnections = ResultRank.WithConnections(position = 4, count = 6),
            ),
            rank,
        )
    }

    @Test
    fun `the combined place is the one the board draws for the player`() {
        playersAt("a" to 30, "b" to 40, ME.uid to 40, "c" to 50)
        cachedConnections(ghost("Ada", 20), ghost("Bo", 40), ghost("Cy", 45), ghost("Di", null))

        val rows = listOf("a" to 30, "b" to 40, ME.uid to 40, "c" to 50)
            .mapIndexed { i, (uid, s) -> DailyBoardRowModel(i + 1, uid, s * 1_000L, 0, uid == ME.uid, playerId = uid) }
        val ghosts = listOf(ghost("Ada", 20), ghost("Bo", 40), ghost("Cy", 45), ghost("Di", null))
            .map { GhostRowModel(it.memberRef, it.name, it.timeMs, it.hintFree) }
        val onBoard = rankWithGhosts(rows, ghosts, fakes.games.rankOrderOf(alpha))
            .filterIsInstance<DailyBoardLine.Real>().first { it.row.isSelf }.place

        val rank = vm(identity = ME, linkedIn = true).heroObserved().heroRank.value as ResultRank.Ranked

        assertEquals(onBoard, rank.withConnections?.position)
    }

    @Test
    fun `a day with no cached connections ranks as before`() {
        playersAt("a" to 30, ME.uid to 40)

        val rank = vm(identity = ME, linkedIn = true).heroObserved().heroRank.value

        assertEquals(ResultRank.Ranked(position = 2, count = 2, topPercent = 100), rank)
    }

    @Test
    fun `with LinkedIn off cached connections are ignored`() {
        playersAt("a" to 30, ME.uid to 40)
        cachedConnections(ghost("Ada", 20))

        val rank = vm(identity = ME, linkedIn = false).heroObserved().heroRank.value

        assertEquals(ResultRank.Ranked(position = 2, count = 2, topPercent = 100), rank)
    }

    @Test
    fun `the result rank never asks LinkedIn for anything`() {
        playersAt("a" to 30, ME.uid to 40)
        cachedConnections(ghost("Ada", 20))

        vm(identity = ME, linkedIn = true).heroObserved()

        assertTrue(linkedInDays.syncCalls.isEmpty())
    }

    @Test
    fun `a play again's rank is read from that day's board, with its cached connections`() {
        val yesterday = day.minusDays(1)
        leaderboard.setEntries(LeaderboardRef(alpha.value, yesterday), listOf(entry("a", 30), entry(ME.uid, 40)))
        linkedInDays.emit(alpha, yesterday, connections(ghost("Ada", 20)))

        val rank = vm(key.copy(day = yesterday.toString()), identity = ME, linkedIn = true).heroObserved().heroRank.value

        assertEquals(
            ResultRank.Ranked(position = 2, count = 2, topPercent = 100, withConnections = ResultRank.WithConnections(position = 3, count = 3)),
            rank,
        )
    }

    @Test
    fun `signed out the rank offers sign in`() {
        assertEquals(ResultRank.SignedOut, vm().heroObserved().heroRank.value)
    }

    @Test
    fun `a play again reads that day's board, not today's`() {
        val yesterday = day.minusDays(1)
        leaderboard.setEntries(LeaderboardRef(alpha.value, yesterday), listOf(entry(ME.uid, 40), entry("a", 50)))
        leaderboard.setEntries(LeaderboardRef(alpha.value, day), listOf(entry("a", 30), entry(ME.uid, 60)))

        val vm = vm(key.copy(day = yesterday.toString()), identity = ME).heroObserved()

        assertEquals(ResultRank.Ranked(position = 1, count = 2, topPercent = 50), vm.heroRank.value)
        assertEquals(listOf(LeaderboardRef(alpha.value, yesterday)), leaderboard.observedBoards.distinct())
    }

    @Test
    fun `a play again on a day the player is not on the board shows no rank`() {
        val yesterday = day.minusDays(1)
        leaderboard.setEntries(LeaderboardRef(alpha.value, yesterday), listOf(entry("a", 30)))

        assertEquals(ResultRank.Hidden, vm(key.copy(day = yesterday.toString()), identity = ME).heroObserved().heroRank.value)
    }

    @Test
    fun `a signed-out play again offers sign in`() {
        assertEquals(ResultRank.SignedOut, vm(key.copy(day = day.minusDays(1).toString())).heroObserved().heroRank.value)
    }

    private val postOn = FakeReleaseFlags(ReleaseFlag.LinkedInPush)

    private fun ResultViewModel.postObserved() = apply {
        screen.launch { linkedInPost.collect {} }
        dispatcher.scheduler.runCurrent()
    }

    @Test
    fun `each post state shows its button state`() {
        val vm = vm(flags = postOn).postObserved()
        val expected = mapOf(
            LinkedInPostState.Ready to LinkedInPostRowState.Post,
            LinkedInPostState.Sending to LinkedInPostRowState.Posting,
            LinkedInPostState.Posted to LinkedInPostRowState.Posted,
            LinkedInPostState.AlreadyOnLinkedIn to LinkedInPostRowState.AlreadyOnLinkedIn,
            LinkedInPostState.Unknown to LinkedInPostRowState.Check,
            LinkedInPostState.Offline to LinkedInPostRowState.Retry,
            LinkedInPostState.Paused to LinkedInPostRowState.Paused,
            LinkedInPostState.NotToday to LinkedInPostRowState.NotToday,
            LinkedInPostState.Tutorial to LinkedInPostRowState.Tutorial,
            LinkedInPostState.Hidden to null,
        )
        for ((state, button) in expected) {
            linkedInPosts.emit(alpha, day, state)
            dispatcher.scheduler.runCurrent()
            assertEquals("$state", button, vm.linkedInPost.value)
        }
    }

    @Test
    fun `a tap on Post submits the slot's day once`() {
        linkedInPosts.emit(alpha, day, LinkedInPostState.Ready)
        val vm = vm(flags = postOn).postObserved()

        vm.postToLinkedIn()
        dispatcher.scheduler.runCurrent()

        assertEquals(listOf(alpha to day), linkedInPosts.submitCalls)
    }

    @Test
    fun `a second tap while the first is sending still submits once`() {
        linkedInPosts.emit(alpha, day, LinkedInPostState.Ready)
        linkedInPosts.submitGate = CompletableDeferred()
        val vm = vm(flags = postOn).postObserved()

        vm.postToLinkedIn()
        dispatcher.scheduler.runCurrent()
        vm.postToLinkedIn() // the button still reads Ready: the store has not said Sending yet
        dispatcher.scheduler.runCurrent()
        linkedInPosts.emit(alpha, day, LinkedInPostState.Sending)
        dispatcher.scheduler.runCurrent()
        vm.postToLinkedIn()
        dispatcher.scheduler.runCurrent()

        assertEquals(1, linkedInPosts.submitCalls.size)
    }

    @Test
    fun `a tap on a status line submits nothing`() {
        for (state in listOf(LinkedInPostState.Sending, LinkedInPostState.Posted, LinkedInPostState.AlreadyOnLinkedIn, LinkedInPostState.Paused, LinkedInPostState.NotToday, LinkedInPostState.Tutorial, LinkedInPostState.Hidden)) {
            linkedInPosts.emit(alpha, day, state)
            val vm = vm(flags = postOn).postObserved()
            vm.postToLinkedIn()
            dispatcher.scheduler.runCurrent()
        }

        assertTrue(linkedInPosts.submitCalls.isEmpty())
    }

    @Test
    fun `Unknown and Offline taps submit again`() {
        for (state in listOf(LinkedInPostState.Unknown, LinkedInPostState.Offline)) {
            linkedInPosts.emit(alpha, day, state)
            vm(flags = postOn).postObserved().postToLinkedIn()
            dispatcher.scheduler.runCurrent()
        }

        assertEquals(2, linkedInPosts.submitCalls.size)
    }

    @Test
    fun `with the flag off the button never shows and nothing is subscribed or submitted`() {
        linkedInPosts.emit(alpha, day, LinkedInPostState.Ready)
        val vm = vm(flags = FakeReleaseFlags()).postObserved()

        vm.postToLinkedIn()
        dispatcher.scheduler.runCurrent()

        assertNull(vm.linkedInPost.value)
        assertEquals(0, observedDays)
        assertTrue(linkedInPosts.submitCalls.isEmpty())
    }

    @Test
    fun `without the LinkedIn bindings the button never shows`() {
        linkedInPosts.emit(alpha, day, LinkedInPostState.Ready)
        val vm = vm().postObserved()

        vm.postToLinkedIn()
        dispatcher.scheduler.runCurrent()

        assertNull(vm.linkedInPost.value)
        assertTrue(linkedInPosts.submitCalls.isEmpty())
    }

    private val matches = FakeMatchRepository().apply { signedInUid = ME.uid }
    private val visibility = FakeVisibilityRepository()
    private var nextThingsBuilt = 0
    private val momentsOn = FakeReleaseFlags(ReleaseFlag.ResultMoments)
    private val ann = ResultPrimaryAction.ChallengeFriend("ann", "ann")

    /** The real next-thing use case over this test's fakes, signed in as [ME]; counted, since only the flag may ask for it. */
    private fun nextThing(games: GameRegistry): ObserveResultNextThing {
        nextThingsBuilt++
        val identity = FakeIdentityService(initial = ME)
        return ObserveResultNextThing(
            registry = games,
            identity = identity,
            history = ObserveGameHistory(games, daily, completions),
            todaySet = todaySet,
            friendPuzzles = ObserveFriendPuzzles(FakeReceivedPuzzleRepository(), saved, completions, games),
            friendUids = ObserveFriendUids(matches, identity),
            leaderboard = leaderboard,
            visibility = visibility,
            computeStreak = ComputeStreak(GetPuzzleDay(games, clock)),
            puzzleDay = GetPuzzleDay(games, clock),
            nextRollover = GetNextRollover(games, clock),
            failures = { _, _, cause -> throw cause },
        )
    }

    /**
     * A slower best stands from yesterday, and the friend ann (tier duel) is ahead on today's board. That best is
     * already under the 45 s mark the solve is under, so the solve is a best and not a first sub-X.
     */
    private fun slowerBestAndAnnAhead(betaSolved: Boolean) {
        val yesterday = record(FakeController.TIME_MS + 2_000, 0, date = day.minusDays(1))
        completions.setRecords(listOfNotNull(yesterday, record(FakeController.TIME_MS, 0, game = beta).takeIf { betaSolved }))
        val code = checkNotNull(ShareCode.parse("AAAAA2"))
        runBlocking {
            matches.create(MatchDraft(code, 24, Player(ME.uid, "me", null), null, listOf(MatchLeg(LegRef.Daily(alpha, day, 7), null)), true, null))
            matches.signedInUid = "ann"
            matches.claim(code, Player("ann", "ann", null))
            matches.signedInUid = ME.uid
        }
        leaderboard.setEntries(LeaderboardRef(alpha.value, day), listOf(entry("ann", 30)))
        visibility.putTier("ann", VisibilityTier.Duel)
    }

    @Test
    fun `with moments on a hint-free best with a duel-tier friend above shows the moments, Challenge as primary and the Challenge row's friend`() {
        slowerBestAndAnnAhead(betaSolved = true)
        solveLive()

        val vm = vm(flags = momentsOn).observed()

        assertEquals(listOf(Moment.PersonalBest, Moment.FullSetDone), vm.nextFacts.value.moments)
        assertEquals(ann, vm.primary.value)
        assertEquals(ann, vm.nextFacts.value.challenge)
    }

    @Test
    fun `with moments off the same solve shows today's primary, no moments and no challenge, and the next thing is never asked for`() {
        slowerBestAndAnnAhead(betaSolved = true)
        solveLive()

        val vm = vm(flags = FakeReleaseFlags()).observed()

        assertEquals(ResultPrimaryAction.AllDone, vm.primary.value)
        assertEquals(ResultNextFacts(), vm.nextFacts.value)
        assertEquals(0, nextThingsBuilt)
    }

    @Test
    fun `with moments on the next daily stays the primary and the friend is offered beside it`() {
        slowerBestAndAnnAhead(betaSolved = false)
        solveLive()

        val vm = vm(flags = momentsOn).observed()

        assertEquals(ResultPrimaryAction.Next(beta, fakes.games.displayName(beta)), vm.primary.value)
        assertEquals(ann, vm.nextFacts.value.challenge)
        assertEquals(listOf(Moment.PersonalBest), vm.nextFacts.value.moments)
    }

    @Test
    fun `with moments on a result with no live play claims no personal best and still gets its next thing`() {
        slowerBestAndAnnAhead(betaSolved = true)

        val vm = vm(flags = momentsOn).observed()

        assertEquals(listOf(Moment.FullSetDone), vm.nextFacts.value.moments)
        assertEquals(ann, vm.primary.value)
    }

    @Test
    fun `with moments on the next thing waits for the record write, so a best still being written is celebrated`() {
        slowerBestAndAnnAhead(betaSolved = true)
        val write = CompletableDeferred<Unit>().also { completions.writeGate = it }
        solveLive()
        val vm = vm(flags = momentsOn).observed()
        assertEquals(ResultNextFacts(), vm.nextFacts.value)

        write.complete(Unit)
        dispatcher.scheduler.runCurrent()

        assertTrue(Moment.PersonalBest in vm.nextFacts.value.moments)
    }

    @Test
    fun `with moments on a play again still offers Done and earns nothing`() {
        slowerBestAndAnnAhead(betaSolved = true)

        val vm = vm(key.copy(day = day.minusDays(1).toString()), flags = momentsOn).observed()

        assertEquals(ResultPrimaryAction.Done, vm.primary.value)
        assertEquals(ResultNextFacts(), vm.nextFacts.value)
        assertEquals(0, nextThingsBuilt)
    }

    @Test
    fun `with moments on a practice puzzle keeps today's next unsolved game and earns nothing`() {
        slowerBestAndAnnAhead(betaSolved = false)
        daily.setHistory(alpha, listOf(DailyEntry(alpha, day, null, "alpha/v1", 0L)))
        solveLive()

        val vm = vm(flags = momentsOn).observed()

        assertEquals(ResultPrimaryAction.Next(beta, fakes.games.displayName(beta)), vm.primary.value)
        assertEquals(ResultNextFacts(primary = vm.primary.value), vm.nextFacts.value)
    }

    @Test
    fun `the celebration plays once for the solve that was handed off, and never for a result opened again`() {
        val celebration = CelebrationOnResult(enabled = true)
        val solve = ActivePlay.Solve(key.timeMs, key.hintsUsed)
        celebration.arm(PlaySlot.Daily(alpha, day), solve)

        val first = vm(celebration = celebration)
        assertTrue(first.celebrating.value)
        first.celebrationDone()
        assertFalse(first.celebrating.value)

        assertFalse("the same result opened again", vm(celebration = celebration).celebrating.value)
    }

    @Test
    fun `no celebration for another solve's result, nor when the celebration is not on Result`() {
        val armed = CelebrationOnResult(enabled = true).apply { arm(PlaySlot.Daily(beta, day), ActivePlay.Solve(key.timeMs, key.hintsUsed)) }
        assertFalse(vm(celebration = armed).celebrating.value)

        val off = CelebrationOnResult(enabled = false).apply { arm(PlaySlot.Daily(alpha, day), ActivePlay.Solve(key.timeMs, key.hintsUsed)) }
        assertFalse(vm(celebration = off).celebrating.value)
        assertFalse(vm().celebrating.value)
    }

    private fun entry(uid: String, seconds: Int) =
        LeaderboardEntry(uid, uid, "alpha/${day}/7", seconds * 1_000L, 0, 1_700_000_000_000)

    private companion object {
        val ME = Identity("me", "Me", null)
    }
}
