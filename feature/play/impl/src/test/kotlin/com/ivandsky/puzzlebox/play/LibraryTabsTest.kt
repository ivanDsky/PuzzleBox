package com.ivandsky.puzzlebox.play

import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.ivandsky.puzzlebox.common.di.commonModule
import com.ivandsky.puzzlebox.common.dispatcher.DispatcherProvider
import com.ivandsky.puzzlebox.data.calendar.GameCalendar
import com.ivandsky.puzzlebox.data.match.MatchRepository
import com.ivandsky.puzzlebox.data.model.PostState
import com.ivandsky.puzzlebox.data.model.ReceivedPuzzle
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
import com.ivandsky.puzzlebox.model.ShareCode
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
import kotlinx.coroutines.runBlocking
import org.junit.After
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
 * friends carousel; with one it shows Dailies, Friends and Duels, Dailies first, each tab
 * swapping the body below the pinned pills, the third carrying the slot's need-you count,
 * and the chosen tab surviving a recreation.
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
    private val today = LocalDate.parse("2026-11-04")

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
                    single<CompletionRepository> { completions }
                    single<GameRegistry> { fakes.games }
                    single<GamePlayRegistry> { fakes.registry }
                    single<SavedPlayRepository> { saved }
                    single<ReceivedPuzzleRepository> { received }
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

    /** What the slot's badge reads; the slot's [DuelsSlot.needYou] follows it. */
    private var needYou by mutableIntStateOf(0)

    private val slot = DuelsSlot(
        content = { Text("duels body", Modifier.testTag(DUELS_BODY)) },
        needYou = { needYou },
        onOpenDuel = {},
    )

    private fun content(duels: DuelsSlot?): @Composable () -> Unit = {
        PuzzleBoxTheme(darkTheme = false) {
            LibraryRoute(layout = PlayLayout.SinglePane, onPlay = { _, _ -> }, onOpenCode = {}, onOpenGameHistory = { _, _ -> }, duels = duels)
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
        listOf("dailies", "friends", "duels", "duels.count").forEach { composeRule.onNodeWithTag("library.tab.$it").assertDoesNotExist() }
    }

    @Test
    fun dailiesTabIsSelectedByDefault() {
        setContent(slot)

        awaitTag("library.row.zip")
        composeRule.onNodeWithTag("library.tab.dailies").assertIsSelected()
        composeRule.onNodeWithTag("library.tab.friends").assertIsNotSelected()
        composeRule.onNodeWithTag("library.tab.duels").assertIsNotSelected()
        composeRule.onNodeWithTag(DUELS_BODY).assertDoesNotExist()
    }

    @Test
    fun tappingATabSwapsTheBody() {
        runBlocking { received.upsert(puzzle(A)) }
        setContent(slot)
        awaitTag("library.row.zip")

        composeRule.onNodeWithTag("library.tab.duels").performClick()
        awaitTag(DUELS_BODY)
        awaitGone("library.row.zip")
        composeRule.onNodeWithTag("library.tab.duels").assertIsSelected()

        composeRule.onNodeWithTag("library.tab.friends").performClick()
        awaitTag("library.friends.row.${A.value}")
        awaitGone(DUELS_BODY)
        composeRule.onNodeWithTag("library.row.zip").assertDoesNotExist()

        composeRule.onNodeWithTag("library.tab.dailies").performClick()
        awaitTag("library.row.zip")
        composeRule.onNodeWithTag("library.friends.row.${A.value}").assertDoesNotExist()
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
        val A = checkNotNull(ShareCode.parse("K7Q2MX"))
    }
}
