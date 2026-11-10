package com.ivandsky.puzzlebox.play

import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.ivandsky.puzzlebox.common.di.commonModule
import com.ivandsky.puzzlebox.common.dispatcher.DispatcherProvider
import com.ivandsky.puzzlebox.data.calendar.GameCalendar
import com.ivandsky.puzzlebox.data.match.MatchRepository
import com.ivandsky.puzzlebox.data.repository.CompletionRepository
import com.ivandsky.puzzlebox.data.repository.DailyRepository
import com.ivandsky.puzzlebox.data.repository.DailyToday
import com.ivandsky.puzzlebox.data.repository.ReceivedPuzzleRepository
import com.ivandsky.puzzlebox.data.repository.SavedPlayRepository
import com.ivandsky.puzzlebox.data.repository.SharedPuzzleRepository
import com.ivandsky.puzzlebox.data.repository.UserPreferencesRepository
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.domain.ComputeStreak
import com.ivandsky.puzzlebox.domain.FinishLeg
import com.ivandsky.puzzlebox.domain.GetPuzzleDay
import com.ivandsky.puzzlebox.domain.LoadPastDay
import com.ivandsky.puzzlebox.domain.ObserveArchiveMonth
import com.ivandsky.puzzlebox.domain.ObserveArchiveProgress
import com.ivandsky.puzzlebox.domain.ObserveGameHistory
import com.ivandsky.puzzlebox.domain.ObserveGameStats
import com.ivandsky.puzzlebox.domain.ObserveTodaySet
import com.ivandsky.puzzlebox.domain.SyncTodayPuzzles
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.gameui.GamePlayRegistry
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.play.di.playModule
import com.ivandsky.puzzlebox.testing.calendar.FakeGameCalendar
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.repository.FakeCompletionRepository
import com.ivandsky.puzzlebox.testing.repository.FakeDailyRepository
import com.ivandsky.puzzlebox.testing.repository.FakeIdentityService
import com.ivandsky.puzzlebox.testing.repository.FakeMatchRepository
import com.ivandsky.puzzlebox.testing.repository.FakeMatchResultOutbox
import com.ivandsky.puzzlebox.testing.repository.FakeReceivedPuzzleRepository
import com.ivandsky.puzzlebox.testing.repository.FakeSavedPlayRepository
import com.ivandsky.puzzlebox.testing.repository.FakeSharedPuzzleRepository
import com.ivandsky.puzzlebox.testing.repository.FakeUserPreferencesRepository
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
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
 * Games acting on a request from outside: a game's page opens over the phone's list (on the
 * Dailies tab) or selects its row beside the tablet's page; a section switches the tab and
 * closes an open page. A game not in the list and a section without tabs are dropped. Every
 * request is taken once, and the same request composed again, or restored after a
 * recreation, does not act twice.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w1280dp-h900dp")
class LibraryRequestTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val zip = GameId("zip")
    private val alpha = TestGames.alpha
    private val fakes = FakeGames(listOf(zip, alpha))
    private val daily = FakeDailyRepository()
    private val today = LocalDate.parse("2026-11-04")

    /** The request the caller holds; a test sets it and never clears it, so a second act would show. */
    private var request by mutableStateOf<LibraryRequest?>(null)

    /** How often Games reported the request taken. */
    private var taken = 0

    /** Bumped to compose the route again with the same request. */
    private var tick by mutableIntStateOf(0)

    private val slot = DuelsSlot(
        content = { Text("duels body", Modifier.testTag(DUELS_BODY)) },
        needYou = { 0 },
    )

    private var koin: KoinApplication? = null

    @Before
    fun setUp() {
        stopKoin() // tests share the JVM-global Koin context
        daily.setToday(zip, DailyToday.Fresh(TestGames.puzzle(zip)))
        daily.setToday(alpha, DailyToday.Fresh(TestGames.puzzle(alpha)))
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
                    single<SharedPuzzleRepository> { FakeSharedPuzzleRepository() }
                    single<MatchRepository> { FakeMatchRepository() }
                    factory { FinishLeg(FakeMatchResultOutbox(), FakeIdentityService()) { Instant.EPOCH } }
                    single<UserPreferencesRepository> { FakeUserPreferencesRepository() }
                    single<GameCalendar> { FakeGameCalendar(today) }
                    factory { LoadPastDay(get(), get()) }
                    single { SyncTodayPuzzles(get(), get(), get()) }
                    factory { ObserveTodaySet(get(), get(), get(), get(), get(), get()) }
                    factory { ObserveGameHistory(get(), get(), get()) }
                    factory { ObserveGameStats(get(), get(), ComputeStreak(GetPuzzleDay(get()))) }
                    factory { ObserveArchiveProgress(get(), get(), get()) }
                    factory { ObserveArchiveMonth(get(), get(), get(), get()) }
                },
                playModule,
            )
        }
    }

    @After
    fun tearDown() {
        koin?.close()
    }

    private fun content(layout: PlayLayout, duels: DuelsSlot?): @Composable () -> Unit = {
        PuzzleBoxTheme(darkTheme = false) {
            Text("tick $tick")
            LibraryRoute(
                layout = layout,
                onPlay = { _, _ -> },
                onOpenCode = {},
                duels = duels,
                request = request,
                onRequestTaken = { taken++ },
            )
        }
    }

    private fun setContent(layout: PlayLayout, duels: DuelsSlot? = null) = composeRule.setContent(content(layout, duels))

    private fun count(tag: String): Int =
        composeRule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().size

    private fun awaitTag(tag: String) = composeRule.waitUntil(5_000L) { count(tag) > 0 }

    private fun awaitGone(tag: String) = composeRule.waitUntil(5_000L) { count(tag) == 0 }

    private fun tap(tag: String) {
        composeRule.onNodeWithTag(tag).performClick()
        composeRule.waitForIdle()
    }

    private fun back() {
        composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun `on the phone a game page request opens the page over the list and back returns to it`() {
        request = LibraryRequest.GamePage("alpha")
        setContent(PlayLayout.SinglePane)

        awaitTag("library.page.alpha")
        composeRule.onNodeWithTag("library.list").assertDoesNotExist()
        assertEquals(1, taken)

        back()
        awaitTag("library.list")
        composeRule.onNodeWithTag("library.page.alpha").assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun `on the phone a game page request opens the page on the Dailies tab`() {
        setContent(PlayLayout.SinglePane, duels = slot)
        awaitTag("library.tab.duels")
        tap("library.tab.duels")
        awaitTag(DUELS_BODY)

        request = LibraryRequest.GamePage("alpha")
        awaitTag("library.page.alpha")
        back()
        awaitTag("library.list")
        composeRule.onNodeWithTag("library.tab.dailies").assertIsSelected()
        composeRule.onNodeWithTag(DUELS_BODY).assertDoesNotExist()
    }

    @Test
    fun `on the tablet a game page request selects the game's row`() {
        request = LibraryRequest.GamePage("alpha")
        setContent(PlayLayout.TwoPane)

        awaitTag("library.page.alpha")
        composeRule.onNodeWithTag("library.row.alpha").assertIsSelected()
        composeRule.onNodeWithTag("library.row.zip").assertIsNotSelected()
        assertEquals(1, taken)
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun `on the phone a game not in the list opens nothing`() {
        request = LibraryRequest.GamePage("nope")
        setContent(PlayLayout.SinglePane)

        awaitTag("library.row.zip")
        composeRule.waitForIdle()
        assertEquals(1, taken)
        composeRule.onNodeWithTag("library.list").assertExists()
        composeRule.onNodeWithTag("library.page.zip").assertDoesNotExist()
        composeRule.onNodeWithTag("library.page.alpha").assertDoesNotExist()
    }

    @Test
    fun `on the tablet a game not in the list keeps the selection rather than falling back to the first row`() {
        setContent(PlayLayout.TwoPane)
        awaitTag("library.row.alpha")
        tap("library.row.alpha")
        awaitTag("library.page.alpha")

        request = LibraryRequest.GamePage("nope")
        composeRule.waitForIdle()
        assertEquals(1, taken)
        composeRule.onNodeWithTag("library.row.alpha").assertIsSelected()
        composeRule.onNodeWithTag("library.row.zip").assertIsNotSelected()
        composeRule.onNodeWithTag("library.page.alpha").assertExists()
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun `a section request switches the tab and the list shows`() {
        request = LibraryRequest.Section(GamesTab.Duels)
        setContent(PlayLayout.SinglePane, duels = slot)

        awaitTag(DUELS_BODY)
        composeRule.onNodeWithTag("library.tab.duels").assertIsSelected()
        composeRule.onNodeWithTag("library.list").assertExists()
        assertEquals(1, taken)
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun `on the phone a section request closes an open page`() {
        setContent(PlayLayout.SinglePane, duels = slot)
        awaitTag("library.row.alpha")
        tap("library.row.alpha")
        awaitTag("library.page.alpha")

        request = LibraryRequest.Section(GamesTab.Duels)
        awaitTag(DUELS_BODY)
        awaitGone("library.page.alpha")
        composeRule.onNodeWithTag("library.tab.duels").assertIsSelected()
        assertEquals(1, taken)
    }

    @Test
    @Config(qualifiers = "w411dp-h240dp")
    fun `a section request leaves the Games list at the top`() {
        setContent(PlayLayout.SinglePane, duels = slot)
        awaitTag("library.row.alpha")
        composeRule.onNodeWithTag("library.row.alpha").performScrollTo()
        composeRule.onNodeWithTag("library.row.zip").assertIsNotDisplayed()

        request = LibraryRequest.Section(GamesTab.Duels)
        awaitTag(DUELS_BODY)
        composeRule.waitForIdle()
        // Opening a page does not scroll, so Dailies shows the offset the request left.
        request = LibraryRequest.GamePage("alpha")
        awaitTag("library.page.alpha")
        back()

        composeRule.onNodeWithTag("library.row.zip").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun `without a Duels slot a section request changes nothing`() {
        setContent(PlayLayout.SinglePane)
        awaitTag("library.row.alpha")
        tap("library.row.alpha")
        awaitTag("library.page.alpha")

        request = LibraryRequest.Section(GamesTab.Duels)
        composeRule.waitForIdle()
        assertEquals(1, taken)
        composeRule.onNodeWithTag("library.page.alpha").assertExists()
        composeRule.onNodeWithTag("library.tab.duels").assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun `a request is taken once and composing it again does not act twice`() {
        val restoration = StateRestorationTester(composeRule)
        request = LibraryRequest.GamePage("alpha")
        restoration.setContent(content(PlayLayout.SinglePane, duels = null))
        awaitTag("library.page.alpha")
        back()
        awaitTag("library.list")

        tick++
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("library.page.alpha").assertDoesNotExist()

        restoration.emulateSavedInstanceStateRestore()
        awaitTag("library.list")
        composeRule.onNodeWithTag("library.page.alpha").assertDoesNotExist()
        assertEquals(1, taken)
    }

    private companion object {
        const val DUELS_BODY = "fake.duels.body"
    }
}
