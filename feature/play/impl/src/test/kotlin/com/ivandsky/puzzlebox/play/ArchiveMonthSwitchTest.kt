package com.ivandsky.puzzlebox.play

import android.provider.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.height
import com.ivandsky.puzzlebox.designsystem.AccentSlot
import com.ivandsky.puzzlebox.designsystem.GameGlyphs
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.domain.GameStats
import com.ivandsky.puzzlebox.domain.PastDayStatus
import com.ivandsky.puzzlebox.domain.TodayStatus
import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.gameui.GameBrand
import com.ivandsky.puzzlebox.gameui.GameCardModel
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.play.domain.ArchiveDay
import com.ivandsky.puzzlebox.play.domain.ArchiveProgress
import com.ivandsky.puzzlebox.testing.game.TestGames
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.abs
import org.junit.After
import org.junit.Assert.assertEquals
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
 * Backlog #1551: paging the archive calendar from one month to the next never flashes. Every frame of the switch has
 * the grid at its full height, drawn (not faded to the card's colour), and showing the month its title names with that
 * month's discs all there: while the asked month's days are still loading the calendar keeps the loaded month whole,
 * and once they land the two change together.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w400dp-h2400dp")
class ArchiveMonthSwitchTest {

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
    private val september = YearMonth.of(2026, 9)
    private val october = YearMonth.of(2026, 10)

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

    /** Every day of [month] in the archive, all solved: the same ink on every month, so a faded grid is the only dip. */
    private fun days(month: YearMonth): List<ArchiveDay> =
        (1..month.lengthOfMonth()).map { month.atDay(it) }.filter { it in range }
            .map { ArchiveDay(it, it.toEpochDay().toInt(), PastDayStatus.Solved(60_000), archived = false, openedOn = null) }

    private fun pageOf(month: YearMonth) = ArchivePageState(game, month, days(month), null)

    private var page by mutableStateOf(pageOf(september))
    private var asked by mutableStateOf<YearMonth?>(september)
    private var card = Color.Unspecified

    private fun show(transition: ArchiveMonthTransition = ArchiveMonthTransition.Crossfade) {
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                card = MaterialTheme.colorScheme.surfaceContainerHigh
                CompositionLocalProvider(LocalArchiveMonthTransition provides transition) {
                    LibraryGamePage(
                        row = row,
                        page = page,
                        month = asked,
                        layout = PlayLayout.SinglePane,
                        calendar = CalendarActions(),
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

    /** Writes the state and hands it to the recomposer now, so the next frame is the first of the switch. */
    private fun flip(change: () -> Unit) = composeRule.runOnIdle {
        change()
        Snapshot.sendApplyNotifications()
    }

    private fun tagStarting(prefix: String) =
        SemanticsMatcher("tag starts with $prefix") { it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith(prefix) == true }

    /** The month the header names, from its tag. */
    private fun titleMonth(): YearMonth {
        val tag = composeRule.onAllNodes(tagStarting("library.calendar.month.")).fetchSemanticsNodes()
            .single().config[SemanticsProperties.TestTag]
        return YearMonth.parse(tag.removePrefix("library.calendar.month."))
    }

    /** How many of [month]'s own days have a disc in the grid right now. */
    private fun discs(month: YearMonth): Int = composeRule.onAllNodes(tagStarting("library.calendar.day.$month-")).fetchSemanticsNodes().size

    /** The pixels that are not the card's colour: marks and numbers, as drawn right now. */
    private fun ink(image: ImageBitmap): Int {
        val pixels = image.toPixelMap()
        var count = 0
        for (y in 0 until pixels.height) for (x in 0 until pixels.width) {
            val p = pixels[x, y]
            if (abs(p.red - card.red) + abs(p.green - card.green) + abs(p.blue - card.blue) > MIN_OFF) count++
        }
        return count
    }

    private fun grid() = composeRule.onNodeWithTag("library.calendar.grid")

    /** The grid before the switch: how tall it is and how much of it is drawn. */
    private class Rest(val height: Float, val ink: Int)

    private fun rest(): Rest = Rest(grid().getBoundsInRoot().height.value, ink(grid().captureToImage()))

    /** One frame of the switch: the grid keeps its height, is drawn at least half as much as at rest, and its month is whole. */
    private fun assertWhole(at: String, rest: Rest) {
        assertEquals("$at: the grid's height", rest.height, grid().getBoundsInRoot().height.value, 0.5f)
        val month = titleMonth()
        assertTrue("$at: the title names $month, ${discs(month)} of its ${days(month).size} discs are there", discs(month) == days(month).size)
        val drawn = ink(grid().captureToImage())
        assertTrue("$at: the grid is drawn $drawn of ${rest.ink} (faded to the card)", drawn * 2 >= rest.ink)
    }

    private fun frames(count: Int, at: String, rest: Rest) {
        repeat(count) {
            composeRule.mainClock.advanceTimeByFrame()
            assertWhole("$at, frame ${it + 1}", rest)
        }
    }

    private fun assertSettled(month: YearMonth, other: YearMonth) {
        composeRule.mainClock.advanceTimeBy(SETTLE_MS)
        assertEquals(month, titleMonth())
        assertEquals("the shown month's discs", days(month).size, discs(month))
        assertEquals("nothing of $other is left", 0, discs(other))
    }

    /** Bug it catches: the new month's grid fading in from the card's colour (every switch dipped to blank on a phone). */
    @Test
    fun `a month switch with its days in hand never fades the grid out or changes its height`() {
        show()
        val rest = rest()
        composeRule.mainClock.autoAdvance = false

        flip {
            page = pageOf(october)
            asked = october
        }
        frames(FRAMES, "days in hand", rest)

        assertSettled(october, september)
    }

    /**
     * Bug it catches: the asked month drawn with no days while they load (a title with an empty grid, then marks popping
     * in), or the old grid dropped for it.
     */
    @Test
    fun `a month asked before its days land keeps the loaded month whole until they do`() {
        show()
        val rest = rest()
        composeRule.mainClock.autoAdvance = false

        flip { asked = october }
        frames(LOADING_FRAMES, "loading", rest)
        assertEquals("the loaded month stays titled while the next loads", september, titleMonth())

        flip { page = pageOf(october) }
        frames(FRAMES, "landed", rest)

        assertSettled(october, september)
    }

    /** Where the disc for [date] is, in dp from the left of the window. */
    private fun left(date: LocalDate) = composeRule.onNodeWithTag("library.calendar.day.$date").getBoundsInRoot().left.value

    /** Pages to [to]; the disc [date] of it, where it is [MID_FRAMES] frames in and where it comes to rest. */
    private fun travel(to: YearMonth, date: LocalDate): Pair<Float, Float> {
        flip {
            page = pageOf(to)
            asked = to
        }
        repeat(MID_FRAMES) { composeRule.mainClock.advanceTimeByFrame() }
        val mid = left(date)
        composeRule.mainClock.advanceTimeBy(SETTLE_MS)
        return mid to left(date)
    }

    /** Bug it catches: the slide variant not moving the incoming grid, or moving it from the wrong side. */
    @Test
    fun `the slide brings a later month in from the end and an earlier one from the start`() {
        show(ArchiveMonthTransition.Slide)
        composeRule.mainClock.autoAdvance = false

        val (forwardMid, forwardRest) = travel(october, october.atDay(15))
        assertTrue("a later month starts to the end of its place: $forwardMid vs $forwardRest", forwardMid > forwardRest + MOVED)

        val (backMid, backRest) = travel(september, september.atDay(15))
        assertTrue("an earlier month starts to the start of its place: $backMid vs $backRest", backMid < backRest - MOVED)
    }

    /** Bug it catches: the crossfade variant sliding too, so there is nothing to tell the two apart on the tablet. */
    @Test
    fun `the crossfade keeps the incoming grid where it will rest`() {
        show(ArchiveMonthTransition.Crossfade)
        composeRule.mainClock.autoAdvance = false

        val (mid, rest) = travel(october, october.atDay(15))
        assertEquals("the new grid does not move", rest, mid, 0.5f)
    }

    /** Bug it catches: reduced motion still travelling (or fading over the long transition) instead of the short fade. */
    @Test
    fun `reduced motion is the short fade for the slide too`() {
        setAnimatorScale(0f)
        show(ArchiveMonthTransition.Slide)
        val rest = rest()
        composeRule.mainClock.autoAdvance = false

        flip {
            page = pageOf(october)
            asked = october
        }
        frames(MID_FRAMES, "reduced", rest)
        val mid = left(october.atDay(15))
        frames(REDUCED_FRAMES, "reduced", rest)

        assertEquals("the new grid never travelled", left(october.atDay(15)), mid, 0.5f)
        assertEquals("the old grid is gone after the short fade, not the long one", 0, discs(september))
    }

    private fun setAnimatorScale(scale: Float) {
        Settings.Global.putFloat(RuntimeEnvironment.getApplication().contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, scale)
    }

    private companion object {
        const val MIN_OFF = 0.05f
        const val FRAMES = 24
        const val LOADING_FRAMES = 8
        const val SETTLE_MS = 600L
        const val MID_FRAMES = 3
        const val MOVED = 4f
        const val REDUCED_FRAMES = 7
    }
}
