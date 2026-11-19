package com.ivandsky.puzzlebox.play

import android.provider.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.height
import com.ivandsky.puzzlebox.designsystem.AccentSlot
import com.ivandsky.puzzlebox.designsystem.GameGlyphs
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.domain.GameStats
import com.ivandsky.puzzlebox.domain.PastDayStatus
import com.ivandsky.puzzlebox.domain.TodayStatus
import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.gameui.GameBrand
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.play.domain.ArchiveDay
import com.ivandsky.puzzlebox.play.domain.ArchiveProgress
import com.ivandsky.puzzlebox.resultui.GameCardModel
import com.ivandsky.puzzlebox.testing.game.TestGames
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.KoinApplication
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Backlog #1668: with the flag on, the archive calendar swipes between months. The grid follows the finger and snaps to the
 * previous or next month on release, ‹ › and the jumps slide it the same way, and the first and last month do not page past
 * the archive. The #1551 rule holds throughout: the title names a month whose discs are all there, at one height.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w400dp-h2400dp")
class ArchiveMonthSwipeTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var koin: KoinApplication? = null

    @Before
    fun startGraph() {
        koin = startGamePageRankKoin(listOf(game))
    }

    @After
    fun stopGraph() {
        setAnimatorScale(1f)
        koin?.close()
    }

    private val game = TestGames.alpha
    private val today = LocalDate.parse("2026-11-05")
    private val range = today.minusDays(200)..today
    private val first = YearMonth.of(2026, 4)
    private val august = YearMonth.of(2026, 8)
    private val september = YearMonth.of(2026, 9)
    private val october = YearMonth.of(2026, 10)
    private val november = YearMonth.of(2026, 11)

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
        progress = ArchiveProgress(range, 201, 0, 0),
        stats = GameStats(0, 0, 0, 0, null, null, null, RankOrder.TimeThenHints),
        rules = emptyList(),
    )

    private fun days(month: YearMonth): List<ArchiveDay> =
        (1..month.lengthOfMonth()).map { month.atDay(it) }.filter { it in range }
            .map { ArchiveDay(it, it.toEpochDay().toInt(), PastDayStatus.Solved(60_000), archived = false, openedOn = null) }

    /** [month] loaded, with its neighbours when [beside]: what the view model emits while the flag is on. */
    private fun pageOf(month: YearMonth, beside: Boolean = true) = ArchivePageState(
        game, month, days(month), null,
        around = if (beside) mapOf(month.minusMonths(1) to days(month.minusMonths(1)), month.plusMonths(1) to days(month.plusMonths(1))) else emptyMap(),
    )

    private var page by mutableStateOf(pageOf(september))
    private var asked by mutableStateOf<YearMonth?>(september)
    private val reported = mutableListOf<YearMonth>()
    private val picked = mutableListOf<LocalDate>()

    /** The view model's side: a month asked for is loaded at once, unless [loads] is off. */
    private fun show(swipe: Boolean = true, loads: Boolean = true) {
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                CompositionLocalProvider(LocalArchiveMonthSwipe provides swipe) {
                    LibraryGamePage(
                        row = row,
                        page = page,
                        month = asked,
                        layout = PlayLayout.SinglePane,
                        calendar = CalendarActions(
                            onShowMonth = {
                                reported += it
                                asked = it
                                if (loads) page = pageOf(it)
                            },
                            onPick = { picked += it },
                        ),
                        onPlay = { _, _ -> },
                        onRetry = {},
                        onOpenLesson = {},
                        onBack = null,
                        offline = false,
                        hasLesson = false,
                        onOpenRanks = { _, _ -> },
                    )
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun showAt(month: YearMonth) {
        page = pageOf(month)
        asked = month
        show()
    }

    private fun tagStarting(prefix: String) =
        SemanticsMatcher("tag starts with $prefix") { it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith(prefix) == true }

    private fun titleMonth(): YearMonth {
        val tag = composeRule.onAllNodes(tagStarting("library.calendar.month.")).fetchSemanticsNodes()
            .single().config[SemanticsProperties.TestTag]
        return YearMonth.parse(tag.removePrefix("library.calendar.month."))
    }

    private fun discs(month: YearMonth): Int = composeRule.onAllNodes(tagStarting("library.calendar.day.$month-")).fetchSemanticsNodes().size

    private fun grid() = composeRule.onNodeWithTag("library.calendar.grid")

    private fun height() = grid().getBoundsInRoot().height.value

    /** Where [month]'s first Tuesday is, in dp from the left of the window: every month's Tuesdays share one column. */
    private fun left(month: YearMonth) =
        composeRule.onNodeWithTag("library.calendar.day.${month.atDay(1).with(TemporalAdjusters.nextOrSame(DayOfWeek.TUESDAY))}")
            .getUnclippedBoundsInRoot().left.value

    private fun swipeAcross(towardStart: Boolean) = grid().performTouchInput {
        val from = if (towardStart) 0.85f else 0.15f
        swipe(Offset(width * from, height * SWIPE_ROW), Offset(width * (1f - from), height * SWIPE_ROW), durationMillis = 120)
    }

    /** One frame of a slide: the grid keeps its height and the title names a month whose discs are all there. */
    private fun assertWhole(at: String, height: Float) {
        assertEquals("$at: the grid's height", height, height(), 0.5f)
        val month = titleMonth()
        assertEquals("$at: the title names $month, its discs", days(month).size, discs(month))
    }

    /** Bug it catches: a swipe that does not page the grid (a calendar that only the arrows move). */
    @Test
    fun `swiping toward the start brings the next month and toward the end the previous one`() {
        show()
        val rest = left(september)

        swipeAcross(towardStart = true)
        composeRule.waitForIdle()
        assertEquals(listOf(october), reported)
        assertEquals(october, titleMonth())
        assertEquals("the next month rests where the last one did", rest, left(october), 0.5f)

        swipeAcross(towardStart = false)
        swipeAcross(towardStart = false)
        composeRule.waitForIdle()
        assertEquals(listOf(october, september, august), reported)
        assertEquals(august, titleMonth())
    }

    /** Bug it catches: the grid jumping a page on release instead of following the finger, or the next month dragging in empty. */
    @Test
    fun `the grid follows the finger with the next month already drawn in`() {
        show()
        val rest = left(september)

        grid().performTouchInput { down(Offset(width * 0.8f, height * SWIPE_ROW)) }
        grid().performTouchInput { moveBy(Offset(-DRAG, 0f)) }
        composeRule.waitForIdle()
        assertEquals("the grid moved with the finger", rest - DRAG, left(september), TOUCH_SLOP)
        assertEquals("the next month is whole while it comes in", days(october).size, discs(october))

        grid().performTouchInput { up() }
    }

    /** Bug it catches: a short drag paging anyway, or leaving the grid stuck between two months. */
    @Test
    fun `a short drag settles back on its month`() {
        show()
        val rest = left(september)

        grid().performTouchInput {
            down(Offset(width * 0.8f, height * SWIPE_ROW))
            moveBy(Offset(-SHORT_DRAG, 0f), delayMillis = HELD_MS)
            up()
        }
        composeRule.waitForIdle()

        assertEquals(emptyList<YearMonth>(), reported)
        assertEquals(september, titleMonth())
        assertEquals("the grid is back at rest", rest, left(september), 0.5f)
    }

    /** Bug it catches: a swipe at the first or last month paging into a month outside the archive. */
    @Test
    fun `the first and last months do not page past the archive`() {
        showAt(november)
        swipeAcross(towardStart = true)
        composeRule.waitForIdle()
        assertEquals(november, titleMonth())
        assertEquals(0, discs(november.plusMonths(1)))

        composeRule.runOnIdle { page = pageOf(first); asked = first }
        composeRule.waitForIdle()
        swipeAcross(towardStart = false)
        composeRule.waitForIdle()
        assertEquals(first, titleMonth())
        assertEquals(0, discs(first.minusMonths(1)))
        assertEquals("nothing was asked for", emptyList<YearMonth>(), reported)
    }

    /** Bug it catches: ‹ › cutting to the month with no travel, or sliding from the wrong side. */
    @Test
    fun `the arrows slide toward the month they step to, at one height`() {
        show()
        val height = height()
        composeRule.mainClock.autoAdvance = false

        composeRule.onNodeWithTag("library.calendar.next").performClick()
        val rest = left(september)
        repeat(MID_FRAMES) {
            composeRule.mainClock.advanceTimeByFrame()
            assertWhole("next, frame ${it + 1}", height)
        }
        assertTrue("it moves toward the start", left(september) < rest - MOVED)
        composeRule.mainClock.advanceTimeBy(SETTLE_MS)
        assertEquals(october, titleMonth())

        composeRule.onNodeWithTag("library.calendar.prev").performClick()
        repeat(MID_FRAMES) {
            composeRule.mainClock.advanceTimeByFrame()
            assertWhole("previous, frame ${it + 1}", height)
        }
        assertTrue("and back toward the end", left(october) > left(september))
        composeRule.mainClock.advanceTimeBy(SETTLE_MS)
        assertEquals(september, titleMonth())
    }

    /** Bug it catches: a far jump scrolling through the months in between (a blur of grids, or a wrong month flashing). */
    @Test
    fun `a jump to number one slides straight there without the months between`() {
        showAt(october)
        val height = height()
        composeRule.mainClock.autoAdvance = false
        val between = listOf(YearMonth.of(2026, 5), YearMonth.of(2026, 6), YearMonth.of(2026, 7), august, september)

        composeRule.onNodeWithTag("library.calendar.jump.first").performClick()
        repeat(JUMP_FRAMES) {
            composeRule.mainClock.advanceTimeByFrame()
            assertWhole("jump, frame ${it + 1}", height)
            between.forEach { month -> assertEquals("frame ${it + 1}: $month is never drawn", 0, discs(month)) }
        }
        composeRule.mainClock.advanceTimeBy(SETTLE_MS)
        assertEquals(first, titleMonth())
        assertEquals(days(first).size, discs(first))
        assertEquals("the old month left with the slide", 0, discs(october))
    }

    /** Bug it catches: the asked month's title and grid changing before its days are in (an empty grid under a new title). */
    @Test
    fun `a month asked for before its days land waits whole, then slides in`() {
        show(loads = false)
        val height = height()
        composeRule.mainClock.autoAdvance = false

        composeRule.onNodeWithTag("library.calendar.next").performClick()
        repeat(MID_FRAMES * 2) {
            composeRule.mainClock.advanceTimeByFrame()
            assertWhole("loading, frame ${it + 1}", height)
        }
        assertEquals(september, titleMonth())

        composeRule.runOnIdle { page = pageOf(october, beside = false) }
        composeRule.mainClock.advanceTimeBy(SETTLE_MS)
        assertEquals(october, titleMonth())
        assertEquals(days(october).size, discs(october))
    }

    /** Bug it catches: the pager swallowing taps, so a day could no longer be picked once the grid swipes. */
    @Test
    fun `a day tap still picks the day`() {
        show()
        composeRule.onNodeWithTag("library.calendar.day.${september.atDay(10)}").performClick()
        assertEquals(listOf(september.atDay(10)), picked)
        assertNull("a tap pages nothing", reported.firstOrNull())
    }

    /** Bug it catches: reduced motion still travelling for the arrows, or a swipe no longer paging. */
    @Test
    fun `reduced motion changes month at once with no travel, and a swipe still pages`() {
        setAnimatorScale(0f)
        show()
        val rest = left(september)
        composeRule.mainClock.autoAdvance = false

        composeRule.onNodeWithTag("library.calendar.next").performClick()
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.mainClock.advanceTimeByFrame()
        assertEquals(october, titleMonth())
        assertEquals("no travel", rest, left(october), 0.5f)

        composeRule.mainClock.autoAdvance = true
        swipeAcross(towardStart = false)
        composeRule.waitForIdle()
        assertEquals(september, titleMonth())
    }

    /** Bug it catches: the flag off still swiping, so the shipped crossfade calendar changes under a finger. */
    @Test
    fun `with the flag off a swipe does nothing`() {
        show(swipe = false)
        swipeAcross(towardStart = true)
        composeRule.waitForIdle()
        assertEquals(emptyList<YearMonth>(), reported)
        assertEquals(september, titleMonth())
    }

    private fun setAnimatorScale(scale: Float) {
        Settings.Global.putFloat(RuntimeEnvironment.getApplication().contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, scale)
    }

    private companion object {
        const val SWIPE_ROW = 0.5f
        const val DRAG = 120f

        // The pager takes the drag only once it passes the touch slop, so the grid trails the finger by up to that.
        const val TOUCH_SLOP = 20f
        const val SHORT_DRAG = 40f
        const val HELD_MS = 400L
        const val MID_FRAMES = 3
        const val JUMP_FRAMES = 12
        const val MOVED = 4f
        const val SETTLE_MS = 800L
    }
}
