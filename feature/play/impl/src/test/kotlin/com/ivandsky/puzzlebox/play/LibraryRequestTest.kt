package com.ivandsky.puzzlebox.play

import com.ivandsky.puzzlebox.data.cloud.CloudSync
import com.ivandsky.puzzlebox.testing.cloud.FakeCloudSync
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.auth.GoogleIdTokenProvider
import com.ivandsky.puzzlebox.common.di.commonModule
import com.ivandsky.puzzlebox.common.dispatcher.DispatcherProvider
import com.ivandsky.puzzlebox.data.calendar.GameCalendar
import com.ivandsky.puzzlebox.data.identity.IdentityService
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardRepository
import com.ivandsky.puzzlebox.data.match.MatchRepository
import com.ivandsky.puzzlebox.data.repository.CompletionRepository
import com.ivandsky.puzzlebox.data.repository.DailyRepository

import com.ivandsky.puzzlebox.data.repository.ReceivedPuzzleRepository
import com.ivandsky.puzzlebox.data.visibility.HiddenPuzzles
import com.ivandsky.puzzlebox.data.repository.SavedPlayRepository
import com.ivandsky.puzzlebox.data.repository.SharedPuzzleRepository
import com.ivandsky.puzzlebox.data.repository.UserPreferencesRepository
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.domain.ComputeStreak
import com.ivandsky.puzzlebox.play.domain.FinishLeg
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
import com.ivandsky.puzzlebox.play.di.playModule
import com.ivandsky.puzzlebox.play.friend.dismissedGoogleSignIn
import com.ivandsky.puzzlebox.testing.calendar.FakeGameCalendar
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
        content = { _, _, _ -> Text("duels body", Modifier.testTag(DUELS_BODY)) },
        needYou = { 0 },
        onOpenDuel = {},
        pane = { _ -> },
    )

    /** What the body of [prefillSlot] was handed, one entry per prefill it took. */
    private val takenPrefills = mutableListOf<DuelPrefill>()

    /** A Duels body that shows the prefill it is handed (or none) and takes it at once, as New duel will. */
    private val prefillSlot = DuelsSlot(
        content = { _, prefill, onTaken ->
            Text(prefill?.friendName ?: "no prefill", Modifier.testTag(PREFILL))
            Text("duels body", Modifier.testTag(DUELS_BODY))
            LaunchedEffect(prefill) {
                if (prefill != null) {
                    takenPrefills += prefill
                    onTaken()
                }
            }
        },
        needYou = { 0 },
        onOpenDuel = {},
        pane = { _ -> },
    )

    /** A Duels body that is a long list of its own, laid out on the state Games hands it. */
    private val scrollingSlot = DuelsSlot(
        content = { state, _, _ ->
            LazyColumn(Modifier.testTag(DUELS_LIST), state = state) {
                items(DUEL_COUNT) { Text("duel $it", Modifier.testTag(duelTag(it)).height(48.dp)) }
            }
        },
        needYou = { 0 },
        onOpenDuel = {},
        pane = { _ -> },
    )

    private var koin: KoinApplication? = null

    @Before
    fun setUp() {
        stopKoin() // tests share the JVM-global Koin context
        daily.setToday(zip, TestGames.puzzle(zip))
        daily.setToday(alpha, TestGames.puzzle(alpha))
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
                    single<MatchRepository> { FakeMatchRepository() }
                    single<UserPreferencesRepository> { FakeUserPreferencesRepository() }
                    single<GameCalendar> { FakeGameCalendar(today) }
                    single { SyncTodayPuzzles(get(), get(), get()) }
                    factory { ObserveTodaySet(get(), get(), get(), get(), get(), get()) }
                    factory { ObserveGameHistory(get(), get(), get()) }
                    factory { ObserveGameStats(get(), get(), ComputeStreak(GetPuzzleDay(get()))) }
                    single<IdentityService> { FakeIdentityService() }
                    single<LeaderboardRepository> { FakeLeaderboardRepository(emptyMap()) }
                    single<GoogleIdTokenProvider> { dismissedGoogleSignIn }
                    factory { ObserveLeaderboard(get(), get(), get(), get()) }
                },
                module { single<CloudSync> { FakeCloudSync() } },
                playModule,
                // After playModule, whose own FinishLeg would replace this one.
                module { factory { FinishLeg(FakeMatchResultOutbox(), FakeSelfMatchRepository(), FakeIdentityService()) { Instant.EPOCH } } },
                hubOffFriendDay,
                flagsOff,
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
                onOpenRanks = { _, _ -> },
                duels = duels,
                request = request,
                onRequestTaken = { taken++ },
                onMakeOne = {},
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
    @Config(qualifiers = "w411dp-h400dp")
    fun `a section request leaves the scrolled Duels list at its first item`() {
        setContent(PlayLayout.SinglePane, duels = scrollingSlot)
        awaitTag("library.tab.duels")
        tap("library.tab.duels")
        awaitTag(DUELS_LIST)
        composeRule.onNodeWithTag(DUELS_LIST).performScrollToIndex(DUEL_COUNT - 1)
        composeRule.onNodeWithTag(duelTag(0)).assertIsNotDisplayed()

        request = LibraryRequest.Section(GamesTab.Duels)
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(duelTag(0)).assertIsDisplayed()
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
        request = LibraryRequest.Section(GamesTab.Duels)
        restoration.setContent(content(PlayLayout.SinglePane, duels = slot))
        awaitTag(DUELS_BODY)
        tap("library.tab.dailies")
        awaitTag("library.list")

        tick++
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("library.tab.dailies").assertIsSelected()

        restoration.emulateSavedInstanceStateRestore()
        awaitTag("library.list")
        composeRule.onNodeWithTag("library.tab.dailies").assertIsSelected()
        assertEquals(1, taken)
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun `a challenge request shows Duels, closes an open page and hands the prefill to the body`() {
        setContent(PlayLayout.SinglePane, duels = prefillSlot)
        awaitTag("library.row.alpha")
        tap("library.row.alpha")
        awaitTag("library.page.alpha")

        request = LibraryRequest.Challenge(ADA_IN_ALPHA)
        awaitTag(DUELS_BODY)
        awaitGone("library.page.alpha")
        composeRule.onNodeWithTag("library.tab.duels").assertIsSelected()
        composeRule.waitForIdle()
        assertEquals(listOf(ADA_IN_ALPHA), takenPrefills)
        assertEquals(1, taken)
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun `a taken prefill is handed to the body once, not again on a recomposition, a tab round trip or a restore`() {
        val restoration = StateRestorationTester(composeRule)
        request = LibraryRequest.Challenge(ADA_IN_ALPHA)
        restoration.setContent(content(PlayLayout.SinglePane, duels = prefillSlot))
        awaitTag(DUELS_BODY)
        composeRule.waitForIdle()
        assertEquals(1, takenPrefills.size)

        tick++
        tap("library.tab.dailies")
        awaitTag("library.list")
        tap("library.tab.duels")
        awaitTag(DUELS_BODY)
        composeRule.onNodeWithTag(PREFILL).assertTextEquals("no prefill")

        restoration.emulateSavedInstanceStateRestore()
        awaitTag(DUELS_BODY)
        composeRule.waitForIdle()
        assertEquals(listOf(ADA_IN_ALPHA), takenPrefills)
        assertEquals(1, taken)
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun `a prefill nobody has taken yet is still pending after a restore`() {
        val restoration = StateRestorationTester(composeRule)
        val holding = DuelsSlot(
            content = { _, prefill, _ -> Text(prefill?.friendName ?: "no prefill", Modifier.testTag(PREFILL)) },
            needYou = { 0 },
            onOpenDuel = {},
            pane = { _ -> },
        )
        request = LibraryRequest.Challenge(ADA_IN_ALPHA)
        restoration.setContent(content(PlayLayout.SinglePane, duels = holding))
        awaitTag(PREFILL)
        composeRule.onNodeWithTag(PREFILL).assertTextEquals("Ada")

        restoration.emulateSavedInstanceStateRestore()
        awaitTag(PREFILL)
        composeRule.onNodeWithTag(PREFILL).assertTextEquals("Ada")
        assertEquals(1, taken)
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun `without a Duels slot a challenge request changes nothing and is taken`() {
        setContent(PlayLayout.SinglePane)
        awaitTag("library.row.alpha")
        tap("library.row.alpha")
        awaitTag("library.page.alpha")

        request = LibraryRequest.Challenge(ADA_IN_ALPHA)
        composeRule.waitForIdle()

        assertEquals(1, taken)
        composeRule.onNodeWithTag("library.page.alpha").assertExists()
        composeRule.onNodeWithTag("library.tab.duels").assertDoesNotExist()
    }

    private companion object {
        const val PREFILL = "fake.duels.prefill"
        val ADA_IN_ALPHA = DuelPrefill(friendUid = "uid-ada", friendName = "Ada", game = TestGames.alpha)
        const val DUELS_BODY = "fake.duels.body"
        const val DUELS_LIST = "fake.duels.list"
        const val DUEL_COUNT = 30

        fun duelTag(index: Int) = "fake.duel.$index"
    }
}
