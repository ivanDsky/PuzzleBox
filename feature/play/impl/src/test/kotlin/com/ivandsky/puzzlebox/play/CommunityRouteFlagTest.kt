package com.ivandsky.puzzlebox.play

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.assertCountEquals
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
import com.ivandsky.puzzlebox.data.repository.DraftRepository
import com.ivandsky.puzzlebox.data.repository.PublishedPuzzleRepository
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
import com.ivandsky.puzzlebox.testing.repository.FakeDraftRepository
import com.ivandsky.puzzlebox.testing.repository.FakeHiddenPuzzles
import com.ivandsky.puzzlebox.testing.repository.FakePublishedPuzzleRepository
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
 * The Community tab (epic community-browse): the shelves home (one Community header, Enter code opening the code sheet),
 * on the tablet a 600dp list pane, and the create FAB behind [PlayFlag.CreateFab].
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

    private fun show(vararg flags: ReleaseFlagKey) = show(previewYoursSlot(count = 0), PlayLayout.SinglePane, *flags)

    private fun show(custom: CustomSlot, layout: PlayLayout, vararg flags: ReleaseFlagKey) {
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
                    single<DraftRepository> { FakeDraftRepository() }
                    single<PublishedPuzzleRepository> { FakePublishedPuzzleRepository() }
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
                CommunityRoute(layout = layout, custom = custom, onOpen = {}, onPlay = {})
            }
        }
    }

    private fun awaitTag(tag: String) {
        composeRule.waitUntil(5_000L) {
            composeRule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun `shows the shelves home`() {
        show()
        awaitTag("community.shelves")
        composeRule.onNodeWithTag("community.shelves").assertIsDisplayed()
    }

    @Test
    fun `the shelves home Enter code opens the code sheet`() {
        show()
        awaitTag("community.enterCode")
        composeRule.onNodeWithTag("community.enterCode").performClick()
        awaitTag("friend.code.sheet")
    }

    @Test
    fun `flag off draws no create FAB even when the host has the action`() {
        show(NewPuzzleStandIn().slot, PlayLayout.SinglePane)
        awaitTag("route.community")
        composeRule.onNodeWithTag("community.fab").assertDoesNotExist()
    }

    @Test
    fun `flag on without a host action draws no create FAB`() {
        show(previewYoursSlot(count = 0), PlayLayout.SinglePane, PlayFlag.CreateFab)
        awaitTag("route.community")
        composeRule.onNodeWithTag("community.fab").assertDoesNotExist()
    }

    @Test
    fun `flag on shows the create FAB and a tap opens the sheet`() {
        val host = NewPuzzleStandIn()
        show(host.slot, PlayLayout.SinglePane, PlayFlag.CreateFab)
        awaitTag("community.fab")
        composeRule.onNodeWithTag("community.fab").assertIsDisplayed().assertHasClickAction()
        composeRule.onNodeWithTag("community.fab").performClick()
        composeRule.onNodeWithTag("new.sheet").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("New puzzle").assertIsDisplayed()
    }

    @Test
    fun `the create FAB is at least 48dp and floats at the bottom end`() {
        show(NewPuzzleStandIn().slot, PlayLayout.SinglePane, PlayFlag.CreateFab)
        awaitTag("community.fab")
        val fab = composeRule.onNodeWithTag("community.fab").getUnclippedBoundsInRoot()
        val root = composeRule.onNodeWithTag("route.community").getUnclippedBoundsInRoot()
        assertTrue(fab.width >= 48.dp && fab.height >= 48.dp)
        assertTrue("bottom end", fab.right <= root.right && fab.bottom <= root.bottom && fab.left > root.width / 2)
    }

    @Test
    fun `the create FAB shows over the shelves home too`() {
        show(NewPuzzleStandIn().slot, PlayLayout.SinglePane, PlayFlag.CreateFab)
        awaitTag("community.shelves")
        composeRule.onNodeWithTag("community.fab").assertIsDisplayed()
    }

    @Test
    @Config(sdk = [34], qualifiers = "w1280dp-h800dp")
    fun `on the tablet the create FAB floats over the shelves list pane, not the detail pane`() {
        show(NewPuzzleStandIn().slot, PlayLayout.TwoPane, PlayFlag.CreateFab)
        awaitTag("community.fab")
        composeRule.onAllNodesWithTag("community.fab").assertCountEquals(1)
        val fab = composeRule.onNodeWithTag("community.fab").getUnclippedBoundsInRoot()
        assertTrue("inside the 600dp list pane: ${fab.right}", fab.right <= ShelvesListPaneWidth + 24.dp + 1.dp)
        composeRule.onNodeWithTag("community.fab").performClick()
        composeRule.onNodeWithTag("new.sheet").assertIsDisplayed()
    }

    @Test
    fun `the create FAB keeps clear of the list end by the clearance the host is told`() {
        val host = NewPuzzleStandIn()
        show(host.slot, PlayLayout.SinglePane, PlayFlag.CreateFab)
        awaitTag("community.fab")
        assertEquals(CreateFabClearance, host.clearance)
        assertTrue(CreateFabClearance >= 56.dp + 16.dp)
    }

    @Test
    fun `draws the Community header once on the phone`() {
        show()
        awaitTag("community.shelves")
        composeRule.onAllNodesWithText("Community").assertCountEquals(1)
        composeRule.onNodeWithTag("community.enterCode").assertIsDisplayed()
    }

    @Test
    fun `with nothing published shows the empty hero on the phone`() {
        show()
        awaitText("NOTHING HERE YET")
    }

    @Test
    @Config(sdk = [34], qualifiers = "w1280dp-h800dp")
    fun `draws the Community header once on the tablet and the list pane is 600dp`() {
        show(previewYoursSlot(count = 0), PlayLayout.TwoPane)
        awaitTag("community.shelves")
        composeRule.onAllNodesWithText("Community").assertCountEquals(1)
        assertEquals(ShelvesListPaneWidth, composeRule.onNodeWithTag("community.shelves").getUnclippedBoundsInRoot().width)
        composeRule.onNodeWithText("Pick a puzzle to see it here").assertIsDisplayed()
    }

    /** Tab S9 Ultra, portrait (about 1112dp wide): the detail pane keeps room beside the 600dp list. */
    @Test
    @Config(sdk = [34], qualifiers = "w1112dp-h1848dp")
    fun `a 600dp list pane leaves the detail pane room on the Tab S9 Ultra in portrait`() = detailLeavesRoom(minDetail = 400.dp)

    /** Tab S9 Ultra, landscape (about 1848dp wide). */
    @Test
    @Config(sdk = [34], qualifiers = "w1848dp-h1112dp")
    fun `a 600dp list pane leaves the detail pane room on the Tab S9 Ultra in landscape`() = detailLeavesRoom(minDetail = 400.dp)

    private fun detailLeavesRoom(minDetail: androidx.compose.ui.unit.Dp) {
        show(previewYoursSlot(count = 0), PlayLayout.TwoPane)
        awaitTag("community.shelves")
        val list = composeRule.onNodeWithTag("community.shelves").getUnclippedBoundsInRoot()
        val root = composeRule.onNodeWithTag("route.community").getUnclippedBoundsInRoot()
        // the panes' spacer (24dp) and end gutter (32dp) sit between the list and the detail
        val detail = root.width - list.right - 56.dp
        assertTrue("detail pane $detail", detail >= minDetail)
    }

    private fun awaitText(text: String) {
        composeRule.waitUntil(5_000L) { composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }
}
