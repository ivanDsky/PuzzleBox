package com.ivandsky.puzzlebox.play

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.ivandsky.puzzlebox.auth.GoogleIdTokenProvider
import com.ivandsky.puzzlebox.common.di.commonModule
import com.ivandsky.puzzlebox.common.dispatcher.DispatcherProvider
import com.ivandsky.puzzlebox.common.flags.ReleaseFlagKey
import com.ivandsky.puzzlebox.common.flags.ReleaseFlags
import com.ivandsky.puzzlebox.data.calendar.GameCalendar
import com.ivandsky.puzzlebox.data.identity.IdentityService
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardRepository
import com.ivandsky.puzzlebox.data.match.MatchRepository
import com.ivandsky.puzzlebox.data.repository.CompletionRepository
import com.ivandsky.puzzlebox.data.repository.DailyRepository
import com.ivandsky.puzzlebox.data.repository.ReceivedPuzzleRepository
import com.ivandsky.puzzlebox.data.repository.SavedPlayRepository
import com.ivandsky.puzzlebox.data.repository.SharedPuzzleRepository
import com.ivandsky.puzzlebox.data.repository.SharedTextStore
import com.ivandsky.puzzlebox.data.repository.UserPreferencesRepository
import com.ivandsky.puzzlebox.data.visibility.HiddenPuzzles
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.domain.ComputeStreak
import com.ivandsky.puzzlebox.domain.GetPuzzleDay
import com.ivandsky.puzzlebox.domain.ObserveGameHistory
import com.ivandsky.puzzlebox.domain.ObserveGameStats
import com.ivandsky.puzzlebox.domain.ObserveLeaderboard
import com.ivandsky.puzzlebox.domain.ObserveTodaySet
import com.ivandsky.puzzlebox.domain.SyncTodayPuzzles
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.gameui.GamePlayRegistry
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.play.domain.FinishLeg
import com.ivandsky.puzzlebox.play.flags.PlayFlag
import com.ivandsky.puzzlebox.play.friend.dismissedGoogleSignIn
import com.ivandsky.puzzlebox.play.di.playModule
import com.ivandsky.puzzlebox.testing.calendar.FakeGameCalendar
import com.ivandsky.puzzlebox.testing.flags.FakeReleaseFlags
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.repository.FakeCompletionRepository
import com.ivandsky.puzzlebox.testing.repository.FakeDailyRepository
import com.ivandsky.puzzlebox.testing.repository.FakeHiddenPuzzles
import com.ivandsky.puzzlebox.testing.repository.FakeIdentityService
import com.ivandsky.puzzlebox.testing.repository.FakeLeaderboardRepository
import com.ivandsky.puzzlebox.testing.repository.FakeMatchRepository
import com.ivandsky.puzzlebox.testing.repository.FakeMatchResultOutbox
import com.ivandsky.puzzlebox.testing.repository.FakeReceivedPuzzleRepository
import com.ivandsky.puzzlebox.testing.repository.FakeSavedPlayRepository
import com.ivandsky.puzzlebox.testing.repository.FakeSelfMatchRepository
import com.ivandsky.puzzlebox.testing.repository.FakeSharedPuzzleRepository
import com.ivandsky.puzzlebox.testing.repository.FakeSharedTextStore
import com.ivandsky.puzzlebox.testing.repository.FakeUserPreferencesRepository
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.KoinApplication
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The Community tab behind [PlayFlag.CommunityShelves] (epic community-browse): off, the route is today's three tabs; on,
 * it is the shelves home stub, whose Enter code opens the same code sheet.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CommunityRouteFlagTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private var koin: KoinApplication? = null

    @After
    fun tearDown() {
        koin?.close()
    }

    private fun show(vararg flags: ReleaseFlagKey) {
        stopKoin() // tests share the JVM-global Koin context
        val zip = GameId("zip")
        val fakes = FakeGames(listOf(zip))
        val daily = FakeDailyRepository().apply { setToday(zip, TestGames.puzzle(zip)) }
        koin = startKoin {
            modules(
                commonModule,
                module {
                    single<CoroutineScope> { CoroutineScope(SupervisorJob() + get<DispatcherProvider>().main) }
                    single<DailyRepository> { daily }
                    single<CompletionRepository> { FakeCompletionRepository() }
                    single<GameRegistry> { fakes.games }
                    single<GamePlayRegistry> { fakes.registry }
                    single<SavedPlayRepository> { FakeSavedPlayRepository() }
                    single<ReceivedPuzzleRepository> { FakeReceivedPuzzleRepository() }
                    single<HiddenPuzzles> { FakeHiddenPuzzles() }
                    single<SharedPuzzleRepository> { FakeSharedPuzzleRepository() }
                    single<SharedTextStore> { FakeSharedTextStore() }
                    single<MatchRepository> { FakeMatchRepository() }
                    single<UserPreferencesRepository> { FakeUserPreferencesRepository() }
                    single<GameCalendar> { FakeGameCalendar(LocalDate.parse("2026-11-04")) }
                    single { SyncTodayPuzzles(get(), get(), get()) }
                    factory { ObserveTodaySet(get(), get(), get(), get(), get(), get()) }
                    factory { ObserveGameHistory(get(), get(), get()) }
                    factory { ObserveGameStats(get(), get(), ComputeStreak(GetPuzzleDay(get()))) }
                    single<IdentityService> { FakeIdentityService() }
                    single<LeaderboardRepository> { FakeLeaderboardRepository(emptyMap()) }
                    single<GoogleIdTokenProvider> { dismissedGoogleSignIn }
                    factory { ObserveLeaderboard(get(), get(), get(), get()) }
                    single<ReleaseFlags> { FakeReleaseFlags(*flags) }
                },
                module { single<com.ivandsky.puzzlebox.data.cloud.CloudSync> { com.ivandsky.puzzlebox.testing.cloud.FakeCloudSync() } },
                playModule,
                // After playModule, whose own FinishLeg would replace this one.
                module { factory { FinishLeg(FakeMatchResultOutbox(), FakeSelfMatchRepository(), FakeIdentityService()) { Instant.EPOCH } } },
                hubOffFriendDay,
            )
        }
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                CommunityRoute(layout = PlayLayout.SinglePane, custom = previewYoursSlot(count = 0), onOpen = {}, onPlay = {})
            }
        }
    }

    private fun awaitTag(tag: String) {
        composeRule.waitUntil(5_000L) {
            composeRule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun `flag off keeps the three tabs and no shelves home`() {
        show()
        awaitTag("route.community")
        composeRule.onNodeWithText("New today").assertIsDisplayed()
        composeRule.onNodeWithText("Friends").assertIsDisplayed()
        composeRule.onNodeWithTag("community.shelves").assertDoesNotExist()
    }

    @Test
    fun `flag on shows the shelves home instead of the tabs`() {
        show(PlayFlag.CommunityShelves)
        awaitTag("community.shelves")
        composeRule.onNodeWithTag("community.shelves").assertIsDisplayed()
        composeRule.onAllNodesWithText("New today").fetchSemanticsNodes().let { assert(it.isEmpty()) { "tabs still drawn" } }
    }

    @Test
    fun `the shelves home Enter code opens the code sheet`() {
        show(PlayFlag.CommunityShelves)
        awaitTag("community.enterCode")
        composeRule.onNodeWithTag("community.enterCode").performClick()
        awaitTag("friend.code.sheet")
    }
}
