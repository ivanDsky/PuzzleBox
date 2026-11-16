package com.ivandsky.puzzlebox.play

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.ivandsky.puzzlebox.common.di.commonModule
import com.ivandsky.puzzlebox.common.flags.ReleaseFlag
import com.ivandsky.puzzlebox.common.flags.ReleaseFlags
import com.ivandsky.puzzlebox.data.calendar.GameCalendar
import com.ivandsky.puzzlebox.data.cloud.CloudSync
import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.data.identity.IdentityService
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardEntry
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardRef
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardRepository
import com.ivandsky.puzzlebox.data.leaderboard.SubmitStatusTracker
import com.ivandsky.puzzlebox.data.match.LegRef
import com.ivandsky.puzzlebox.data.match.MatchDraft
import com.ivandsky.puzzlebox.data.match.MatchLeg
import com.ivandsky.puzzlebox.data.match.MatchRepository
import com.ivandsky.puzzlebox.data.match.Player
import com.ivandsky.puzzlebox.data.repository.CompletionRepository
import com.ivandsky.puzzlebox.data.repository.CustomBoardRepository
import com.ivandsky.puzzlebox.data.repository.DailyEntry
import com.ivandsky.puzzlebox.data.repository.DailyRepository
import com.ivandsky.puzzlebox.data.repository.ReceivedPuzzleRepository
import com.ivandsky.puzzlebox.data.repository.SavedPlayRepository
import com.ivandsky.puzzlebox.data.repository.SharedPuzzleRepository
import com.ivandsky.puzzlebox.data.repository.UserPreferencesRepository
import com.ivandsky.puzzlebox.data.visibility.VisibilityRepository
import com.ivandsky.puzzlebox.data.visibility.VisibilityTier
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.domain.ComputeStreak
import com.ivandsky.puzzlebox.domain.FinishLeg
import com.ivandsky.puzzlebox.domain.GetNextRollover
import com.ivandsky.puzzlebox.domain.GetPuzzleDay
import com.ivandsky.puzzlebox.domain.LoadPastDay
import com.ivandsky.puzzlebox.domain.ObserveGameHistory
import com.ivandsky.puzzlebox.domain.ObserveGameStats
import com.ivandsky.puzzlebox.domain.ObserveLeaderboard
import com.ivandsky.puzzlebox.domain.ObserveResultInsights
import com.ivandsky.puzzlebox.domain.ObserveResultRank
import com.ivandsky.puzzlebox.domain.ObserveTodaySet
import com.ivandsky.puzzlebox.domain.SyncTodayPuzzles
import com.ivandsky.puzzlebox.domain.moments.Moment
import com.ivandsky.puzzlebox.domain.nextthing.ObserveResultNextThing
import com.ivandsky.puzzlebox.domain.ranks.ObserveFriendUids
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.game.today
import com.ivandsky.puzzlebox.gameui.GameBrand
import com.ivandsky.puzzlebox.gameui.GamePlayRegistry
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.model.ShareCode
import com.ivandsky.puzzlebox.play.api.GameKey
import com.ivandsky.puzzlebox.play.api.ResultKey
import com.ivandsky.puzzlebox.play.di.playModule
import com.ivandsky.puzzlebox.play.friend.dismissedGoogleSignIn
import com.ivandsky.puzzlebox.testing.calendar.FakeGameCalendar
import com.ivandsky.puzzlebox.testing.cloud.FakeCloudSync
import com.ivandsky.puzzlebox.testing.flags.FakeReleaseFlags
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.repository.FakeCompletionRepository
import com.ivandsky.puzzlebox.testing.repository.FakeCustomBoardRepository
import com.ivandsky.puzzlebox.testing.repository.FakeDailyRepository
import com.ivandsky.puzzlebox.testing.repository.FakeIdentityService
import com.ivandsky.puzzlebox.testing.repository.FakeLeaderboardRepository
import com.ivandsky.puzzlebox.testing.repository.FakeMatchRepository
import com.ivandsky.puzzlebox.testing.repository.FakeMatchResultOutbox
import com.ivandsky.puzzlebox.testing.repository.FakeReceivedPuzzleRepository
import com.ivandsky.puzzlebox.testing.repository.FakeSavedPlayRepository
import com.ivandsky.puzzlebox.testing.repository.FakeSharedPuzzleRepository
import com.ivandsky.puzzlebox.testing.repository.FakeUserPreferencesRepository
import com.ivandsky.puzzlebox.testing.repository.FakeVisibilityRepository
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * L2 on Result: the celebration is as long as the moments known on the phone make it (a friend-board moment never
 * lengthens it), one tap ends it, and a daily solve's hand-off plays it over a Result that is already drawn.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class ResultCelebrationTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val alpha = TestGames.alpha
    private val beta = TestGames.beta
    private val day = TestGames.definition(alpha).cadence.today()
    private var dones = 0

    @After
    fun closeGraph() = stopKoin()

    private fun celebrate(moments: List<Moment>) {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                ResultCelebration(brand = GameBrand.Neutral, moments = moments, onDone = { dones++ })
            }
        }
        composeRule.mainClock.advanceTimeBy(16)
    }

    @Test
    fun `with no moment it ends after one second`() {
        celebrate(emptyList())
        composeRule.mainClock.advanceTimeBy(900)
        assertEquals("still playing", 0, dones)

        composeRule.mainClock.advanceTimeBy(200)
        assertEquals(1, dones)
    }

    @Test
    fun `a moment the phone knows makes it two seconds`() {
        celebrate(listOf(Moment.PersonalBest))
        composeRule.mainClock.advanceTimeBy(1_900)
        assertEquals("a moment outlasts the normal length", 0, dones)

        composeRule.mainClock.advanceTimeBy(200)
        assertEquals(1, dones)
    }

    @Test
    fun `friend-board moments never lengthen it`() {
        celebrate(listOf(Moment.TopThreeFriends(1), Moment.PassedFriend("ann", "Ann")))
        composeRule.mainClock.advanceTimeBy(900)
        assertEquals(0, dones)

        composeRule.mainClock.advanceTimeBy(200)
        assertEquals(1, dones)
    }

    @Test
    fun `one tap skips it`() {
        celebrate(listOf(Moment.PersonalBest))
        composeRule.mainClock.advanceTimeBy(200)
        composeRule.onNodeWithTag("solve.celebration").performClick()
        composeRule.mainClock.advanceTimeBy(16)
        assertEquals(1, dones)

        composeRule.mainClock.advanceTimeBy(5_000)
        assertEquals("tap and timer end it once between them", 1, dones)
    }

    @Test
    fun `a daily solve plays the celebration over a drawn Result, a tap ends it, and the Challenge row hands back that friend and the game`() {
        val fakes = FakeGames(listOf(alpha, beta))
        startGraph(fakes)
        var result by mutableStateOf<ResultKey?>(null)
        var challenged: DuelPrefill? = null
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                when (val key = result) {
                    null -> GameRoute(
                        key = GameKey(alpha.value),
                        layout = PlayLayout.SinglePane,
                        onBack = {},
                        onSolved = { _, timeMs, hints -> result = ResultKey(alpha.value, day.toString(), timeMs, hints) },
                    )
                    else -> ResultRoute(
                        key = key,
                        onPlayAgain = {},
                        onNext = {},
                        onDone = {},
                        onOpenReplay = {},
                        onOpenRanks = { _, _ -> },
                        onChallenge = { challenged = it },
                    )
                }
            }
        }
        composeRule.waitForIdle()

        // The clock is held from the solve on, so the celebration's own second cannot pass unseen.
        composeRule.mainClock.autoAdvance = false
        composeRule.runOnIdle { fakes[alpha].latest.win() }
        repeat(6) {
            composeRule.mainClock.advanceTimeByFrame()
            composeRule.waitForIdle()
        }
        composeRule.onNodeWithTag("solve.celebration").assertExists()
        composeRule.onNodeWithTag("result.hero.screen").assertExists()

        composeRule.onNodeWithTag("solve.celebration").performClick()
        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("solve.celebration").assertDoesNotExist()

        composeRule.waitUntil(5_000) { composeRule.onAllNodesWithTag("result.challenge").fetchSemanticsNodes().isNotEmpty() }
        composeRule.onNodeWithTag("result.next").assertExists()
        composeRule.onNodeWithTag("result.challenge").performScrollTo().performClick()
        composeRule.runOnIdle { assertEquals(DuelPrefill("ann", "ann", alpha), challenged) }
    }

    /** Result moments on, signed in as "me", with the friend ann (tier duel) ahead on alpha's board today. */
    private fun startGraph(fakes: FakeGames) {
        val me = Identity("me", "Me", null)
        val identity = FakeIdentityService(initial = me)
        val daily = FakeDailyRepository().apply {
            listOf(alpha, beta).forEach { game ->
                setToday(game, TestGames.puzzle(game))
                setHistory(game, listOf(DailyEntry(game, day, 7, "${game.value}/v1", 0L)))
            }
        }
        val matches = FakeMatchRepository().apply { signedInUid = me.uid }
        val code = checkNotNull(ShareCode.parse("AAAAA2"))
        runBlocking {
            matches.create(MatchDraft(code, 24, Player(me.uid, "me", null), null, listOf(MatchLeg(LegRef.Daily(alpha, day, 7), null)), true, null))
            matches.signedInUid = "ann"
            matches.claim(code, Player("ann", "ann", null))
            matches.signedInUid = me.uid
        }
        val leaderboard = FakeLeaderboardRepository().apply {
            setEntries(LeaderboardRef(alpha.value, day), listOf(LeaderboardEntry("ann", "ann", "alpha/$day/7", 30_000L, 0, 1L)))
        }
        val visibility = FakeVisibilityRepository().apply { putTier("ann", VisibilityTier.Duel) }
        stopKoin() // tests share the JVM-global Koin context
        startKoin {
            modules(
                commonModule,
                module {
                    single<GameRegistry> { fakes.games }
                    single<GamePlayRegistry> { fakes.registry }
                    single<DailyRepository> { daily }
                    single<CompletionRepository> { FakeCompletionRepository() }
                    single<SavedPlayRepository> { FakeSavedPlayRepository() }
                    single<ReceivedPuzzleRepository> { FakeReceivedPuzzleRepository() }
                    single<SharedPuzzleRepository> { FakeSharedPuzzleRepository() }
                    single<MatchRepository> { matches }
                    single<UserPreferencesRepository> { FakeUserPreferencesRepository() }
                    factory { FinishLeg(FakeMatchResultOutbox(), FakeIdentityService()) { Instant.EPOCH } }
                    single<GameCalendar> { FakeGameCalendar(day) }
                    factory { LoadPastDay(get(), get()) }
                    single { SyncTodayPuzzles(get(), get(), get()) }
                    factory { ObserveTodaySet(get(), get(), get(), get(), get(), get()) }
                    factory { ObserveGameHistory(get(), get(), get()) }
                    factory { GetPuzzleDay(get()) }
                    factory { ComputeStreak(get()) }
                    factory { ObserveGameStats(get(), get(), get()) }
                    factory { GetNextRollover(get()) }
                    single<ReleaseFlags> { FakeReleaseFlags(ReleaseFlag.ResultMoments) }
                    factory { loadSolveReplay(get(), get(), get()) }
                    single<IdentityService> { identity }
                    single<LeaderboardRepository> { leaderboard }
                    single<VisibilityRepository> { visibility }
                    single { SubmitStatusTracker() }
                    single<CustomBoardRepository> { FakeCustomBoardRepository() }
                    single { dismissedGoogleSignIn }
                    factory { ObserveLeaderboard(get(), get(), get(), get()) }
                    factory { ObserveResultRank(get(), get(), friendDay = null) } // no Friends/Global setting
                    factory { ObserveResultInsights(get(), get(), get(), get()) }
                    factory { ObserveFriendUids(get(), get()) }
                    factory {
                        ObserveResultNextThing(
                            registry = get(), identity = get(), history = get(), todaySet = get(), friendPuzzles = get(),
                            friendUids = get(), leaderboard = get(), visibility = get(), computeStreak = get(),
                            puzzleDay = get(), nextRollover = get(),
                        )
                    }
                },
                module { single<CloudSync> { FakeCloudSync() } },
                playModule,
                hubOffFriendDay,
            )
        }
    }
}
