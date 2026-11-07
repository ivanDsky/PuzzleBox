package com.ivandsky.puzzlebox.play

import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import com.ivandsky.puzzlebox.common.di.commonModule
import com.ivandsky.puzzlebox.common.flags.ReleaseFlag
import com.ivandsky.puzzlebox.common.flags.ReleaseFlags
import com.ivandsky.puzzlebox.data.calendar.GameCalendar
import com.ivandsky.puzzlebox.data.model.PostState
import com.ivandsky.puzzlebox.data.model.ReceivedPuzzle
import com.ivandsky.puzzlebox.data.repository.CompletionRepository
import com.ivandsky.puzzlebox.data.repository.DailyRepository
import com.ivandsky.puzzlebox.data.repository.DailyToday
import com.ivandsky.puzzlebox.data.repository.ReceivedPuzzleRepository
import com.ivandsky.puzzlebox.data.repository.SavedPlayRepository
import com.ivandsky.puzzlebox.data.repository.UserPreferencesRepository
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.domain.ComputeStreak
import com.ivandsky.puzzlebox.domain.GetPuzzleDay
import com.ivandsky.puzzlebox.domain.LoadPastDay
import com.ivandsky.puzzlebox.domain.ObserveArchiveMonth
import com.ivandsky.puzzlebox.domain.ObserveArchiveProgress
import com.ivandsky.puzzlebox.domain.ObserveGameHistory
import com.ivandsky.puzzlebox.domain.ObserveGameStats
import com.ivandsky.puzzlebox.domain.ObserveTodaySet
import com.ivandsky.puzzlebox.domain.SyncTodayPuzzles
import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.game.RecordStats
import com.ivandsky.puzzlebox.gameui.GamePlayRegistry
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.PlaySlot
import com.ivandsky.puzzlebox.model.PuzzleKey
import com.ivandsky.puzzlebox.model.ShareCode
import com.ivandsky.puzzlebox.play.di.playModule
import com.ivandsky.puzzlebox.testing.calendar.FakeGameCalendar
import com.ivandsky.puzzlebox.testing.flags.FakeReleaseFlags
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.repository.FakeCompletionRepository
import com.ivandsky.puzzlebox.testing.repository.FakeDailyRepository
import com.ivandsky.puzzlebox.testing.repository.FakeReceivedPuzzleRepository
import com.ivandsky.puzzlebox.testing.repository.FakeSavedPlayRepository
import com.ivandsky.puzzlebox.testing.repository.FakeUserPreferencesRepository
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.KoinApplication
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * From friends in the Play library (spec A19): the section lists every received puzzle with
 * its status and is hidden while there is none; a friend puzzle's saved play shows on its
 * card, never in a daily's status; a card opens the puzzle's card by its code. A long press
 * removes the puzzle with its saved play and offers Undo, which brings both back; once the
 * window closes both stay gone and the completion record stays. With friend sharing off
 * (ADR-0022) the section never shows.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class LibraryFromFriendsTest {

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

    /** Every code a card opened, in order. */
    private val opened = ArrayList<ShareCode>()

    private var koin: KoinApplication? = null

    /** Friend sharing on unless a test turns it off before setContent. */
    private var flags = FakeReleaseFlags(ReleaseFlag.FriendSharing)

    @Before
    fun setUp() {
        stopKoin() // tests share the JVM-global Koin context
        daily.setToday(zip, DailyToday.Fresh(TestGames.puzzle(zip)))
        daily.setToday(alpha, DailyToday.Fresh(TestGames.puzzle(alpha)))
        koin = startKoin {
            modules(
                commonModule,
                module {
                    single<ReleaseFlags> { flags }
                    single<DailyRepository> { daily }
                    single<CompletionRepository> { completions }
                    single<GameRegistry> { fakes.games }
                    single<GamePlayRegistry> { fakes.registry }
                    single<SavedPlayRepository> { saved }
                    single<ReceivedPuzzleRepository> { received }
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

    private fun setContent() {
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                LibraryRoute(layout = PlayLayout.SinglePane, onPlay = { _, _ -> }, onOpenCode = { opened.add(it) })
            }
        }
    }

    private fun awaitTag(tag: String) {
        composeRule.waitUntil(5_000L) {
            composeRule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun count(tag: String): Int =
        composeRule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().size

    @Test
    fun `the section is hidden while nothing came from friends`() {
        setContent()
        awaitTag("card.status_mark.not_started")
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("library.friends").assertDoesNotExist()
    }

    @Test
    fun `the section lists each received puzzle with its status`() {
        runBlocking {
            received.upsert(puzzle(A, openedAt = 1))
            received.upsert(puzzle(B, openedAt = 2, game = alpha))
            completions.onCompleted(win(B, alpha))
        }
        setContent()
        awaitTag("library.friends")

        awaitTag("library.friends.${A.value}.status.new")
        awaitTag("library.friends.${B.value}.status.solved")
        composeRule.onNodeWithTag("library.friends.row.${A.value}").assertExists()
        composeRule.onNodeWithTag("library.friends.row.${B.value}").assertExists()
    }

    @Test
    fun `with friend sharing off the section stays hidden though puzzles came from friends`() {
        runBlocking { received.upsert(puzzle(A, openedAt = 1)) }
        flags = FakeReleaseFlags()
        setContent()
        awaitTag("card.status_mark.not_started")
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("library.friends").assertDoesNotExist()
    }

    @Test
    fun `a friend puzzle's saved play shows on its card and never as a daily in progress`() {
        runBlocking {
            received.upsert(puzzle(A))
            saved.save(PlaySlot.Custom(zip, A.value), byteArrayOf(1), elapsedMs = 70_000)
        }
        setContent()
        awaitTag("library.friends.${A.value}.status.in_progress")
        awaitTag("card.status_mark.not_started")

        assertEquals("both dailies read not started", 2, count("card.status_mark.not_started"))
        assertEquals(0, count("card.status_mark.in_progress"))
    }

    @Test
    fun `a card opens its puzzle by code`() {
        runBlocking { received.upsert(puzzle(A)) }
        setContent()
        awaitTag("library.friends.row.${A.value}")

        composeRule.onNodeWithTag("library.friends.row.${A.value}").performClick()
        composeRule.waitForIdle()

        assertEquals(listOf(A), opened)
    }

    @Test
    fun `a long press removes the puzzle and Undo brings it back in progress`() {
        runBlocking {
            received.upsert(puzzle(A))
            saved.save(slotA, byteArrayOf(1), elapsedMs = 70_000)
        }
        setContent()
        awaitTag("library.friends.${A.value}.status.in_progress")

        remove(A)

        awaitTag("library.friends.removed")
        awaitGone("library.friends.row.${A.value}")
        assertTrue("the row is deleted", received.rows.isEmpty())
        assertTrue("its saved play is deleted", saved.rows.isEmpty())

        composeRule.onNodeWithTag("library.friends.removed.undo").performClick()

        awaitTag("library.friends.${A.value}.status.in_progress")
        awaitGone("library.friends.removed")
        assertNotNull(received.rows[A])
        assertEquals(mapOf(slotA to 70_000L), runBlocking { saved.observeInProgress().first() })
    }

    @Test
    fun `once the Undo window closes the puzzle stays removed and its record stays`() {
        runBlocking {
            received.upsert(puzzle(A))
            saved.save(slotA, byteArrayOf(1), elapsedMs = 70_000)
            completions.onCompleted(win(A, zip))
        }
        setContent()
        awaitTag("library.friends.row.${A.value}")

        remove(A)
        awaitTag("library.friends.removed")
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(FriendRemoval.UNDO_WINDOW_MS))

        awaitGone("library.friends.removed")
        composeRule.onNodeWithTag("library.friends").assertDoesNotExist()
        assertTrue(received.rows.isEmpty())
        assertTrue(saved.rows.isEmpty())
        assertEquals(listOf(win(A, zip)), runBlocking { completions.observeCustom(zip, A.value).first() })
    }

    private val slotA get() = PlaySlot.Custom(zip, A.value)

    private fun remove(code: ShareCode) {
        composeRule.onNodeWithTag("library.friends.row.${code.value}").performTouchInput { longClick() }
        awaitTag("library.friends.${code.value}.remove")
        composeRule.onNodeWithTag("library.friends.${code.value}.remove").performClick()
    }

    private fun awaitGone(tag: String) {
        composeRule.waitUntil(5_000L) { count(tag) == 0 }
    }

    private fun puzzle(code: ShareCode, openedAt: Long = 0, game: GameId = zip) = ReceivedPuzzle(
        code = code,
        gameId = game,
        variant = TestGames.variant(game),
        sharedFormatVersion = 1,
        puzzleBytes = byteArrayOf(TestGames.MAGIC),
        title = "Crown",
        authorName = "Bo",
        note = "",
        authorTimeMs = 100_000,
        authorHints = 0,
        authorUid = "author",
        openedAt = Instant.ofEpochMilli(1_700_000_000_000 + openedAt),
        unpublished = false,
        postState = PostState.Pending,
        postAttempts = 0,
        difficulty = null,
    )

    private fun win(code: ShareCode, game: GameId) = CompletionRecord(
        puzzleRef = TestGames.Ref(game, PuzzleKey.Custom(code.value)),
        time = 125_000,
        hints = 0,
        stats = RecordStats(1, "{}"),
        isRecord = true,
    )

    private companion object {
        val A = checkNotNull(ShareCode.parse("K7Q2MX"))
        val B = checkNotNull(ShareCode.parse("B4R9TZ"))
    }
}
