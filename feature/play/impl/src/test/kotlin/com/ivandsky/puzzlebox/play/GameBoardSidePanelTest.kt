package com.ivandsky.puzzlebox.play

import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.ivandsky.puzzlebox.common.di.commonModule
import com.ivandsky.puzzlebox.data.calendar.GameCalendar
import com.ivandsky.puzzlebox.data.cloud.CloudSync
import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.data.leaderboard.SubmitStatusTracker
import com.ivandsky.puzzlebox.data.match.MatchRepository
import com.ivandsky.puzzlebox.data.repository.CompletionRepository
import com.ivandsky.puzzlebox.data.repository.DailyEntry
import com.ivandsky.puzzlebox.data.repository.DailyRepository
import com.ivandsky.puzzlebox.data.repository.ReceivedPuzzleRepository
import com.ivandsky.puzzlebox.data.repository.SavedPlayRepository
import com.ivandsky.puzzlebox.data.repository.SharedPuzzleRepository
import com.ivandsky.puzzlebox.data.repository.UserPreferencesRepository
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.play.domain.FinishLeg
import com.ivandsky.puzzlebox.domain.LoadPastDay
import com.ivandsky.puzzlebox.domain.ObserveGameHistory
import com.ivandsky.puzzlebox.domain.ObserveLeaderboard
import com.ivandsky.puzzlebox.play.domain.ObservePlayStats
import com.ivandsky.puzzlebox.domain.ObserveTodaySet
import com.ivandsky.puzzlebox.domain.SyncTodayPuzzles
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.game.today
import com.ivandsky.puzzlebox.gameui.GamePlayRegistry
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.play.di.playModule
import com.ivandsky.puzzlebox.play.api.GameKey
import com.ivandsky.puzzlebox.play.friend.dismissedGoogleSignIn
import com.ivandsky.puzzlebox.testing.calendar.FakeGameCalendar
import com.ivandsky.puzzlebox.testing.cloud.FakeCloudSync
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.repository.FakeCompletionRepository
import com.ivandsky.puzzlebox.testing.repository.FakeDailyRepository
import com.ivandsky.puzzlebox.testing.repository.FakeIdentityService
import com.ivandsky.puzzlebox.testing.repository.FakeLeaderboardRepository
import com.ivandsky.puzzlebox.testing.repository.FakeMatchRepository
import com.ivandsky.puzzlebox.testing.repository.FakeMatchResultOutbox
import com.ivandsky.puzzlebox.testing.repository.FakeReceivedPuzzleRepository
import com.ivandsky.puzzlebox.testing.repository.FakeSavedPlayRepository
import com.ivandsky.puzzlebox.testing.repository.FakeSelfMatchRepository
import com.ivandsky.puzzlebox.testing.repository.FakeSharedPuzzleRepository
import com.ivandsky.puzzlebox.testing.repository.FakeUserPreferencesRepository
import java.time.Instant
import kotlin.time.Duration.Companion.seconds
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The two-pane side panel [GameBoard] picks: a self-duel leg gets its own time and hints cards and never builds the
 * stats panel's view model, a friend leg keeps the stats panel.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w1280dp-h800dp-mdpi")
class GameBoardSidePanelTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val alpha = TestGames.alpha
    private val day = TestGames.definition(alpha).cadence.today()
    private val fakes = FakeGames(listOf(alpha))
    private val daily = FakeDailyRepository().apply {
        setToday(alpha, TestGames.puzzle(alpha))
        setHistory(alpha, listOf(DailyEntry(alpha, day, 7, "alpha/v1", 0L)))
    }
    private val calendar = FakeGameCalendar(day)
    private val completions = FakeCompletionRepository()
    private val identity = FakeIdentityService(Identity("me", "Ivan", photoUrl = null))

    @Before
    fun setUp() {
        stopKoin()
        startKoin {
            modules(
                commonModule,
                module {
                    single<DailyRepository> { daily }
                    single<CompletionRepository> { completions }
                    single<GameRegistry> { fakes.games }
                    single<GamePlayRegistry> { GamePlayRegistry(listOf(ScaffoldedUi(fakes[alpha]))) }
                    single<SavedPlayRepository> { FakeSavedPlayRepository() }
                    single<ReceivedPuzzleRepository> { FakeReceivedPuzzleRepository() }
                    single<SharedPuzzleRepository> { FakeSharedPuzzleRepository() }
                    single<MatchRepository> { FakeMatchRepository() }
                    single<GameCalendar> { calendar }
                    single<UserPreferencesRepository> { FakeUserPreferencesRepository() }
                    factory { LoadPastDay(get(), get()) }
                },
                module { single<CloudSync> { FakeCloudSync() } },
                playModule,
                // After playModule, whose own FinishLeg would replace this one.
                module { factory { FinishLeg(FakeMatchResultOutbox(), FakeSelfMatchRepository(), identity) { Instant.EPOCH } } },
                hubOffFriendDay,
                // The stats panel's view model over fakes; a self leg must never ask for it.
                module {
                    viewModel { params ->
                        PlayStatsViewModel(
                            slot = params.get<com.ivandsky.puzzlebox.model.PlaySlot>(),
                            registry = fakes.games,
                            calendar = calendar,
                            observePlayStats = ObservePlayStats(
                                todaySet = ObserveTodaySet(fakes.games, FakeSavedPlayRepository(), completions, calendar, daily, SyncTodayPuzzles(fakes.games, daily, calendar)),
                                history = ObserveGameHistory(fakes.games, daily, completions),
                                leaderboard = ObserveLeaderboard(identity, FakeLeaderboardRepository(emptyMap()), fakes.games, calendar),
                            ),
                            posts = SubmitStatusTracker(),
                            identity = identity,
                            idTokens = dismissedGoogleSignIn,
                            linkedIn = null,
                        )
                    }
                },
            )
        }
    }

    @After
    fun tearDown() = stopKoin()

    private fun show(layout: PlayLayout, selfLeg: SelfLeg?) {
        val play = org.koin.core.context.GlobalContext.get().get<PlaySessionStore>().open(GameKey(alpha.value))
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                GameBoard(
                    play = play,
                    pastDay = false,
                    tutorial = TutorialGate.Closed,
                    onHowToPlay = null,
                    onTutorialFinished = {},
                    layout = layout,
                    onBack = {},
                    onSolved = { _, _, _ -> },
                    overflow = emptyList(),
                    modifier = Modifier,
                    selfLeg = selfLeg,
                )
            }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun `a two-pane self leg shows its own progress and hints and no stats panel`() {
        show(PlayLayout.TwoPane, SelfLeg(target = 134.seconds, played = 1, count = 3))

        composeRule.onNodeWithTag("leg.progress").assertExists()
        composeRule.onNodeWithTag("leg.hints").assertExists()
        composeRule.onNodeWithTag("play.stats").assertDoesNotExist()
    }

    @Test
    fun `a two-pane friend leg keeps the stats panel and no leg cards`() {
        show(PlayLayout.TwoPane, selfLeg = null)

        composeRule.onNodeWithTag("play.stats").assertExists()
        composeRule.onNodeWithTag("leg.progress").assertDoesNotExist()
    }

    @Test
    fun `a phone self leg builds no side panel`() {
        show(PlayLayout.SinglePane, SelfLeg(target = 134.seconds, played = 1, count = 3))

        composeRule.onNodeWithTag("leg.progress").assertDoesNotExist()
        composeRule.onNodeWithTag("play.stats").assertDoesNotExist()
    }
}
