package com.ivandsky.puzzlebox.play

import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.ivandsky.puzzlebox.data.linkedin.LinkState
import com.ivandsky.puzzlebox.data.linkedin.LinkedInDayData
import com.ivandsky.puzzlebox.data.linkedin.LinkedInOwnState
import com.ivandsky.puzzlebox.data.repository.DailyEntry
import com.ivandsky.puzzlebox.designsystem.AccentSlot
import com.ivandsky.puzzlebox.designsystem.GameGlyphs
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.domain.ArchiveDay
import com.ivandsky.puzzlebox.domain.ArchiveProgress
import com.ivandsky.puzzlebox.domain.GameStats
import com.ivandsky.puzzlebox.domain.ObserveArchiveMonth
import com.ivandsky.puzzlebox.domain.PastDayStatus
import com.ivandsky.puzzlebox.domain.TodayStatus
import com.ivandsky.puzzlebox.domain.linkedin.LinkedInDayView
import com.ivandsky.puzzlebox.domain.linkedin.ObserveLinkedInDay
import com.ivandsky.puzzlebox.domain.linkedin.SavedBoardState
import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.gameui.GameBrand
import com.ivandsky.puzzlebox.gameui.GameCardModel
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.play.linkedin.LinkedInCardState
import com.ivandsky.puzzlebox.testing.calendar.FakeGameCalendar
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.linkedin.FakeLinkedInDays
import com.ivandsky.puzzlebox.testing.repository.FakeCompletionRepository
import com.ivandsky.puzzlebox.testing.repository.FakeDailyRepository
import com.ivandsky.puzzlebox.testing.repository.FakeSavedPlayRepository
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.KoinApplication
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * F3: a day that has a saved LinkedIn board draws the same archive disc, and the calendar the same legend, as a day
 * without one; the saved board lives on the day card's board line only. The disc kind `saved` still means a cached
 * puzzle (`PastDayStatus.Stored`).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w400dp-h800dp")
class ArchiveSavedBoardDiscTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var koin: KoinApplication? = null

    @Before
    fun startGraph() {
        koin = startGamePageBoardKoin(listOf(game))
    }

    @After
    fun stopGraph() {
        koin?.close()
    }

    private val game = TestGames.alpha
    private val today = LocalDate.parse("2026-11-05")
    private val month = YearMonth.from(today)

    /** A day with a saved, final LinkedIn board, a day without one, and a day whose puzzle is cached on the device. */
    private val withBoard = today.minusDays(3)
    private val without = today.minusDays(2)
    private val cached = today.minusDays(1)

    private val daily = FakeDailyRepository().apply {
        setArchiveRange(game, today.minusDays(40)..today)
        setCached(game, cached, TestGames.puzzle(game))
        addEntry(DailyEntry(game, cached, null, variant = "v", fetchedAtMs = 0L, archived = false))
    }
    private val linkedIn = FakeLinkedInDays()

    private val row = LibraryRowModel(
        card = GameCardModel(
            game = game,
            name = "Alpha",
            brand = GameBrand(AccentSlot.Blue, GameGlyphs.Zip),
            status = TodayStatus.NotStarted,
            rankOrder = RankOrder.TimeThenHints,
            puzzleNumber = 10,
            best = null,
        ),
        today = 10,
        progress = ArchiveProgress(today.minusDays(40)..today, 41, 0, 0),
        stats = GameStats(0, 0, 0, 0, null, null, null, RankOrder.TimeThenHints),
        rules = emptyList(),
    )

    private val boardView = MutableStateFlow<LinkedInDayView?>(null)

    private fun archiveDays(): List<ArchiveDay> = runBlocking {
        ObserveArchiveMonth(daily, FakeSavedPlayRepository(), FakeCompletionRepository(), FakeGameCalendar(today))(game, month).first()
    }

    /** The day's view as the day card gets it, over a saved, final board read at 2026-11-04 14:12 UTC. */
    private fun savedFinalView(on: LocalDate): LinkedInDayView {
        linkedIn.emit(
            game,
            on,
            LinkedInDayData(
                link = LinkState.NotConnected,
                delta = 3,
                boardInWindow = true,
                inFlight = false,
                lastSync = null,
                own = LinkedInOwnState.Unknown,
                ghosts = emptyList(),
                boardFetchedAtMs = 1_793_801_520_000L,
                boardComplete = true,
            ),
        )
        val view = runBlocking { ObserveLinkedInDay(linkedIn, FakeGames(listOf(game)).games)(game, on).first() }
        assertTrue("the fixture really is a saved, final board: ${view.saved}", view.saved.let { it is SavedBoardState.Saved && it.final })
        return view
    }

    private fun show(days: List<ArchiveDay>, picked: ArchiveDay?) {
        composeRule.setContent {
            PuzzleBoxTheme {
                LibraryGamePage(
                    row = row,
                    page = ArchivePageState(game, month, days, picked),
                    month = month,
                    layout = PlayLayout.SinglePane,
                    calendar = CalendarActions(),
                    onPlay = { _, _ -> },
                    onRetry = {},
                    onOpenLesson = {},
                    onBack = null,
                    offline = false,
                    hasLesson = false,
                    onOpenGameHistory = { _, _ -> },
                    linkedIn = LinkedInCardState(boardView, onResume = {}, onRetry = {}, onReconnect = {}),
                )
            }
        }
        composeRule.waitForIdle()
    }

    private fun discState(day: LocalDate): String? =
        composeRule.onNodeWithTag("library.calendar.day.$day").fetchSemanticsNode().config.getOrNull(SemanticsProperties.StateDescription)

    private fun SemanticsNode.texts(): List<String> =
        config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } + children.flatMap { it.texts() }

    /** What the calendar draws as text: day numbers, "#N", the title and the legend. */
    private fun calendarTexts(): List<String> = composeRule.onNodeWithTag("library.calendar", useUnmergedTree = true).fetchSemanticsNode().texts()

    /** Every disc's state, in date order. */
    private fun discStates(days: List<ArchiveDay>): List<Pair<LocalDate, String?>> = days.map { it.date to discState(it.date) }

    @Test
    fun aDayWithASavedBoardDrawsTheSameDiscAndLegendAsADayWithout() {
        val days = archiveDays()
        assertEquals(
            "the month knows nothing of a board",
            days.first { it.date == without }.status,
            days.first { it.date == withBoard }.status,
        )
        val picked = days.first { it.date == withBoard }

        show(days, picked)
        val before = discStates(days)
        val textsBefore = calendarTexts()

        boardView.value = savedFinalView(withBoard)
        composeRule.waitForIdle()

        assertEquals("the saved board repaints no disc", before, discStates(days))
        assertEquals("the saved board changes no legend or number", textsBefore, calendarTexts())
        assertEquals("not_opened", discState(without))
        assertEquals("the day with a saved board has the disc of the day without", discState(without), discState(withBoard))
    }

    @Test
    fun noLinkedInMarkIsDrawnInsideTheCalendar() {
        val days = archiveDays()
        show(days, days.first { it.date == withBoard })
        boardView.value = savedFinalView(withBoard)
        composeRule.waitForIdle()

        composeRule.onAllNodes(
            SemanticsMatcher("a linkedin node") { it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("linkedin.") == true } and
                hasAnyAncestor(hasTestTag("library.calendar")),
            useUnmergedTree = true,
        ).assertCountEquals(0)
    }

    @Test
    fun theLegendNamesTheFourDiscsAndNoSavedBoard() {
        val days = archiveDays()
        show(days, days.first { it.date == withBoard })
        boardView.value = savedFinalView(withBoard)
        composeRule.waitForIdle()

        val texts = calendarTexts()
        listOf("Solved", "In progress", "Saved on this device", "Not opened yet").forEach { label ->
            assertEquals("legend entry $label", 1, texts.count { it == label })
        }
        assertTrue("no legend entry names a board: $texts", texts.none { "board" in it.lowercase() || "LinkedIn" in it })
    }

    @Test
    fun theSavedDiscStillMeansACachedPuzzle() {
        val days = archiveDays()
        assertEquals(PastDayStatus.Stored, days.first { it.date == cached }.status)
        show(days, days.first { it.date == withBoard })
        boardView.value = savedFinalView(withBoard)
        composeRule.waitForIdle()

        assertEquals("saved", discState(cached))
        assertEquals("a saved board is not a saved disc", "not_opened", discState(withBoard))
    }
}
