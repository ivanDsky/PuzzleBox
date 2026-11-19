package com.ivandsky.puzzlebox.play

import com.ivandsky.puzzlebox.data.cloud.CloudSync
import com.ivandsky.puzzlebox.testing.cloud.FakeCloudSync
import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.compete.api.DuelKey
import com.ivandsky.puzzlebox.common.di.commonModule
import com.ivandsky.puzzlebox.common.dispatcher.DispatcherProvider
import com.ivandsky.puzzlebox.data.calendar.GameCalendar
import com.ivandsky.puzzlebox.data.identity.IdentityService
import com.ivandsky.puzzlebox.data.match.LegRef
import com.ivandsky.puzzlebox.data.match.MatchDraft
import com.ivandsky.puzzlebox.data.match.MatchLeg
import com.ivandsky.puzzlebox.data.match.MatchRepository
import com.ivandsky.puzzlebox.data.match.Player
import com.ivandsky.puzzlebox.data.model.PostState
import com.ivandsky.puzzlebox.data.model.ReceivedPuzzle
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
import com.ivandsky.puzzlebox.domain.ObserveTodaySet
import com.ivandsky.puzzlebox.domain.SyncTodayPuzzles
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.gameui.GamePlayRegistry
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.ShareCode
import com.ivandsky.puzzlebox.play.di.playModule
import com.ivandsky.puzzlebox.testing.calendar.FakeGameCalendar
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.repository.FakeCompletionRepository
import com.ivandsky.puzzlebox.testing.repository.FakeDailyRepository
import com.ivandsky.puzzlebox.testing.repository.FakeHiddenPuzzles
import com.ivandsky.puzzlebox.testing.repository.FakeIdentityService
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
import kotlinx.coroutines.runBlocking
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
 * The Games screen's tabs: with no Duels slot the screen is the single list with its From
 * friends carousel; with one it shows Dailies and Duels, Dailies first, each tab
 * swapping the body below the pinned pills, the second carrying the slot's need-you count,
 * and the chosen tab surviving a recreation. The header's Enter code still opens a code
 * that names a duel.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class LibraryTabsTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val zip = GameId("zip")
    private val alpha = TestGames.alpha
    private val fakes = FakeGames(listOf(zip, alpha))
    private val daily = FakeDailyRepository()
    private val received = FakeReceivedPuzzleRepository()
    private val saved = FakeSavedPlayRepository()
    private val completions = FakeCompletionRepository()
    private val matches = FakeMatchRepository().apply { signedInUid = ANN }
    private val today = LocalDate.parse("2026-11-04")

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
                    single<CompletionRepository> { completions }
                    single<GameRegistry> { fakes.games }
                    single<GamePlayRegistry> { fakes.registry }
                    single<SavedPlayRepository> { saved }
                    single<ReceivedPuzzleRepository> { received }
                    single<HiddenPuzzles> { FakeHiddenPuzzles() }
                    single<SharedPuzzleRepository> { FakeSharedPuzzleRepository() }
                    single<MatchRepository> { matches }
                    single<IdentityService> { FakeIdentityService() }
                    single<UserPreferencesRepository> { FakeUserPreferencesRepository() }
                    single<GameCalendar> { FakeGameCalendar(today) }
                    single { SyncTodayPuzzles(get(), get(), get()) }
                    factory { ObserveTodaySet(get(), get(), get(), get(), get(), get()) }
                    factory { ObserveGameHistory(get(), get(), get()) }
                    factory { ObserveGameStats(get(), get(), ComputeStreak(GetPuzzleDay(get()))) }
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

    /** What the slot's badge reads; the slot's [DuelsSlot.needYou] follows it. */
    private var needYou by mutableIntStateOf(0)

    private val slot = DuelsSlot(
        content = { _, _, _ -> Text("duels body", Modifier.testTag(DUELS_BODY)) },
        needYou = { needYou },
        onOpenDuel = {},
        pane = { _ -> },
    )

    private fun content(duels: DuelsSlot?): @Composable () -> Unit = {
        PuzzleBoxTheme(darkTheme = false) {
            LibraryRoute(
                layout = PlayLayout.SinglePane,
                onPlay = { _, _ -> },
                onOpenCode = {},
                onOpenRanks = { _, _ -> },
                duels = duels,
                onMakeOne = {},
            )
        }
    }

    private fun setContent(duels: DuelsSlot?) = composeRule.setContent(content(duels))

    private fun count(tag: String): Int =
        composeRule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().size

    private fun awaitTag(tag: String) = composeRule.waitUntil(5_000L) { count(tag) > 0 }

    private fun awaitGone(tag: String) = composeRule.waitUntil(5_000L) { count(tag) == 0 }

    @Test
    fun noDuelsSlotShowsNoTabs() {
        runBlocking { received.upsert(puzzle(A)) }
        setContent(duels = null)

        awaitTag("library.friends.carousel")
        awaitTag("library.row.zip")
        listOf("dailies", "duels", "duels.count").forEach { composeRule.onNodeWithTag("library.tab.$it").assertDoesNotExist() }
    }

    @Test
    fun dailiesTabIsSelectedByDefault() {
        setContent(slot)

        awaitTag("library.row.zip")
        composeRule.onNodeWithTag("library.tab.dailies").assertIsSelected()
        composeRule.onNodeWithTag("library.tab.duels").assertIsNotSelected()
        composeRule.onNodeWithTag(DUELS_BODY).assertDoesNotExist()
    }

    @Test
    fun tappingATabSwapsTheBody() {
        setContent(slot)
        awaitTag("library.row.zip")

        composeRule.onNodeWithTag("library.tab.duels").performClick()
        awaitTag(DUELS_BODY)
        awaitGone("library.row.zip")
        composeRule.onNodeWithTag("library.tab.duels").assertIsSelected()

        composeRule.onNodeWithTag("library.tab.dailies").performClick()
        awaitTag("library.row.zip")
        awaitGone(DUELS_BODY)
    }

    @Test
    fun theSubtitleNamesTheSelectedTab() {
        setContent(slot)
        awaitTag("library.row.zip")
        composeRule.onNodeWithText("Every puzzle, back to #1").assertExists()

        composeRule.onNodeWithTag("library.tab.duels").performClick()
        awaitTag(DUELS_BODY)
        awaitGone("library.row.zip")
        composeRule.onNodeWithText("Head-to-head, same puzzle").assertExists()
        composeRule.onNodeWithText("Every puzzle, back to #1").assertDoesNotExist()

        composeRule.onNodeWithTag("library.tab.dailies").performClick()
        awaitTag("library.row.zip")
        composeRule.onNodeWithText("Every puzzle, back to #1").assertExists()
    }

    @Test
    fun duelsPillShowsTheNeedYouCount() {
        needYou = 2
        setContent(slot)
        awaitTag("library.tab.duels.count")

        needYou = 0
        awaitGone("library.tab.duels.count")
    }

    @Test
    fun tappingTheDuelsCountBadgeSelectsTheDuelsTab() {
        needYou = 3
        setContent(slot)
        awaitTag("library.tab.duels.count")

        composeRule.onNodeWithTag("library.tab.duels.count", useUnmergedTree = true).performClick()

        awaitTag(DUELS_BODY)
        composeRule.onNodeWithTag("library.tab.duels").assertIsSelected()
    }

    @Test
    fun duelsPillNamesItsCountForAccessibility() {
        needYou = 3
        setContent(slot)
        awaitTag("library.tab.duels.count")

        composeRule.onNodeWithTag("library.tab.duels").assertContentDescriptionEquals("Duels, 3 need you")

        needYou = 12
        composeRule.onNodeWithTag("library.tab.duels").assertContentDescriptionEquals("Duels, 12 need you")
    }

    @Test
    fun everyGamesPillIsAtLeast48dpTall() {
        needYou = 1
        setContent(slot)
        awaitTag("library.tab.duels.count")

        listOf("dailies", "duels").forEach { composeRule.onNodeWithTag("library.tab.$it").assertHeightIsAtLeast(48.dp) }
    }

    @Test
    fun selectedTabSurvivesRecreation() {
        val restoration = StateRestorationTester(composeRule)
        restoration.setContent(content(slot))
        awaitTag("library.row.zip")
        composeRule.onNodeWithTag("library.tab.duels").performClick()
        awaitTag(DUELS_BODY)

        restoration.emulateSavedInstanceStateRestore()

        awaitTag(DUELS_BODY)
        composeRule.onNodeWithTag("library.tab.duels").assertIsSelected()
        composeRule.onNodeWithTag("library.row.zip").assertDoesNotExist()
    }

    @Test
    fun theHeadersEnterCodeStillOpensADuelCode() {
        val openedDuels = ArrayList<DuelKey>()
        val legs = listOf(MatchLeg(LegRef.Daily(zip, today, 1), authorUid = null))
        runBlocking { matches.create(MatchDraft(A, 24, Player(ANN, "Ann", null), invitedUid = null, legs, includeFriends = false, rematchOf = null)) }
        setContent(
            duels = DuelsSlot(content = { _, _, _ -> }, needYou = { 0 }, onOpenDuel = { openedDuels.add(it) }, pane = { _ -> }),
        )
        awaitTag("library.enterCode")

        composeRule.onNodeWithTag("library.enterCode").performClick()
        awaitTag("friend.code.field")
        composeRule.onNodeWithTag("friend.code.field", useUnmergedTree = true).performTextInput(A.value)
        composeRule.onNodeWithTag("friend.code.open", useUnmergedTree = true).performClick()

        // The check comes back on the main looper, which only an idle wait runs.
        composeRule.waitUntil(5_000L) {
            composeRule.waitForIdle()
            openedDuels.isNotEmpty()
        }
        assertEquals(listOf(DuelKey(A.value)), openedDuels)
    }

    private fun puzzle(code: ShareCode) = ReceivedPuzzle(
        code = code,
        gameId = zip,
        variant = TestGames.variant(zip),
        sharedFormatVersion = 1,
        puzzleBytes = byteArrayOf(TestGames.MAGIC),
        title = "Crown",
        authorName = "Bo",
        note = "",
        authorTimeMs = 100_000,
        authorHints = 0,
        authorUid = "author",
        openedAt = Instant.ofEpochMilli(1_700_000_000_000),
        unpublished = false,
        postState = PostState.Pending,
        postAttempts = 0,
        difficulty = null,
    )

    private companion object {
        const val DUELS_BODY = "fake.duels.body"
        const val ANN = "ann"
        val A = checkNotNull(ShareCode.parse("K7Q2MX"))
    }
}
