package com.ivandsky.puzzlebox.gameui.board

import android.content.Context
import android.graphics.Color
import android.provider.Settings
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.height
import androidx.test.core.app.ApplicationProvider
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.game.RankOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * What the list does when its lines change, on a paused clock: a row whose place changes slides there, a row that
 * arrives expands in, a row that leaves shrinks out and is then gone, the pinned row does the same, the own row slides
 * between the top list and the pinned place, and on another day's board the people on both days slide, the others
 * arrive and leave. With animation off, or with reduced motion, the change lands at once.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class DailyBoardListMotionTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var lines by mutableStateOf<List<DailyBoardLine>>(emptyList())
    private var pinned by mutableStateOf<DailyBoardLine.Real?>(null)

    private fun real(player: String, seconds: Int = 10, self: Boolean = false) =
        DailyBoardLine.Real(DailyBoardRowModel(1, player, seconds * 1000L, 0, self, playerId = player), 1)

    private fun show(initial: List<DailyBoardLine>, animated: Boolean = true, reducedMotion: Boolean = false) {
        if (reducedMotion) {
            val context = ApplicationProvider.getApplicationContext<Context>()
            Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        }
        lines = initial
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                DailyBoardList(lines, RankOrder.TimeThenHints, pinned = pinned, animated = animated)
            }
        }
        // The first lines are on show from the start; the rows' own entrance, if any, is played out.
        composeRule.mainClock.advanceTimeBy(SETTLED_MS)
    }

    private fun change(block: () -> Unit) {
        composeRule.runOnUiThread(block)
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeByFrame()
    }

    private fun settle() = composeRule.mainClock.advanceTimeBy(SETTLED_MS)

    private fun top(player: String): Dp = composeRule.onNodeWithText(player).getBoundsInRoot().top

    private fun listHeight(): Dp = composeRule.onNodeWithTag("daily_board.list").getBoundsInRoot().height

    private fun count(player: String) = composeRule.onAllNodesWithText(player).fetchSemanticsNodes().size

    @Test
    fun `a row whose place changes slides there instead of jumping`() {
        show(listOf(real("ada"), real("bo"), real("cy")))
        val from = top("cy")
        val to = top("ada")

        change { lines = listOf(real("cy"), real("ada"), real("bo")) }
        composeRule.mainClock.advanceTimeBy(60)

        val during = top("cy")
        assertTrue("cy at $during, between its places $to and $from", during < from && during > to)
        settle()
        assertEquals(to, top("cy"))
    }

    @Test
    fun `a row that arrives expands in and ends at its full size`() {
        show(listOf(real("ada"), real("bo")))
        val before = listHeight()

        change { lines = lines + real("cy") }
        composeRule.mainClock.advanceTimeBy(EARLY_MS)

        val during = listHeight()
        assertTrue("height $during grows from $before", during > before)
        settle()
        val after = listHeight()
        assertTrue("height $after is the full three rows, past the $during seen mid-way", after > during)
        assertEquals(1, count("cy"))
    }

    @Test
    fun `a row that leaves is still there mid-way and gone once it has shrunk out`() {
        show(listOf(real("ada"), real("bo"), real("cy")))
        val full = listHeight()

        change { lines = listOf(real("ada"), real("cy")) }
        composeRule.mainClock.advanceTimeBy(60)

        assertEquals("bo is still on its way out", 1, count("bo"))
        assertTrue("height ${listHeight()} shrinks from $full", listHeight() < full)
        settle()
        assertEquals(0, count("bo"))
        assertTrue(listHeight() < full)
    }

    @Test
    fun `the pinned row and its divider expand in and shrink out`() {
        show(listOf(real("ada")))
        val bare = listHeight()

        change { pinned = real("me", self = true) }
        composeRule.mainClock.advanceTimeBy(EARLY_MS)
        val during = listHeight()
        assertTrue("height $during grows from $bare", during > bare)
        settle()
        assertTrue(listHeight() > during)
        composeRule.onNodeWithTag("leaderboard.you").assertExists()

        change { pinned = null }
        composeRule.mainClock.advanceTimeBy(60)
        composeRule.onNodeWithTag("leaderboard.you").assertExists()
        settle()
        assertEquals(bare, listHeight())
        assertEquals(0, composeRule.onAllNodesWithTag("leaderboard.you").fetchSemanticsNodes().size)
    }

    // The darkest pixel of the person's name: a name drawn at part alpha is lighter than one drawn whole.
    private fun ink(player: String): Int {
        val image = composeRule.onNodeWithText(player).captureToImage().asAndroidBitmap()
        var darkest = 255
        for (x in 0 until image.width) for (y in 0 until image.height) {
            val pixel = image.getPixel(x, y)
            darkest = minOf(darkest, (Color.red(pixel) + Color.green(pixel) + Color.blue(pixel)) / 3)
        }
        return darkest
    }

    private fun switchDay() = change {
        lines = listOf(real("cy", 9), real("ada", 11), real("di", 12))
    }

    @Test
    fun `switching day slides a person on both days between the two places and never fades them`() {
        show(listOf(real("ada"), real("bo"), real("cy")))
        val start = top("ada")
        val inkBefore = ink("ada")

        switchDay()
        composeRule.mainClock.advanceTimeBy(60)

        val during = top("ada")
        assertEquals("ada is one row on both days", 1, count("ada"))
        assertTrue("ada at $during, past its old place $start", during > start)
        assertTrue("ink ${ink("ada")} stays the ink $inkBefore of a row not fading", ink("ada") <= inkBefore + 8)
        settle()
        assertTrue("ada at ${top("ada")}, past the $during seen mid-way", top("ada") > during)
    }

    @Test
    fun `switching day fades out a person only on the old day`() {
        show(listOf(real("ada"), real("bo"), real("cy")))
        val inkBefore = ink("bo")

        switchDay()
        composeRule.mainClock.advanceTimeBy(60)

        assertEquals("bo is still on its way out", 1, count("bo"))
        assertTrue("ink ${ink("bo")} lighter than the whole $inkBefore", ink("bo") > inkBefore + 30)
        settle()
        assertEquals(0, count("bo"))
    }

    @Test
    fun `switching day fades in a person only on the new day`() {
        show(listOf(real("ada"), real("bo"), real("cy")))

        switchDay()
        composeRule.mainClock.advanceTimeBy(60)
        val during = ink("di")
        settle()

        assertEquals(1, count("di"))
        assertTrue("ink $during lighter than the settled ${ink("di")}", during > ink("di") + 30)
    }

    @Test
    fun `the own row slides from the top list to the pinned place instead of leaving and arriving`() {
        show(listOf(real("ada"), real("bo"), real("me", self = true)))
        val from = top("me")

        change {
            lines = listOf(real("ada"), real("bo"))
            pinned = real("me", self = true)
        }
        composeRule.mainClock.advanceTimeBy(60)

        assertEquals("me is one row, not one leaving and one arriving", 1, count("me"))
        settle()
        assertEquals(1, count("me"))
        assertTrue("me at ${top("me")}, under the divider past $from", top("me") > from)
    }

    @Test
    fun `the same day with the same players is one set of rows`() {
        show(listOf(real("ada"), real("bo")))

        change { lines = listOf(real("bo"), real("ada")) }
        composeRule.mainClock.advanceTimeBy(60)

        assertEquals(1, count("ada"))
        assertEquals(1, count("bo"))
    }

    @Test
    fun `reduced motion lands every change at once`() {
        show(listOf(real("ada"), real("bo"), real("cy")), reducedMotion = true)
        val full = listHeight()

        change { lines = listOf(real("cy"), real("ada")) }

        assertEquals(0, count("bo"))
        assertEquals(1, count("cy"))
        assertTrue(top("cy") < top("ada"))
        assertTrue(listHeight() < full)
        val settled = listHeight()
        settle()
        assertEquals(settled, listHeight())

        change { pinned = real("me", self = true) }
        assertTrue(listHeight() > settled)
        change { pinned = null }
        assertEquals(settled, listHeight())
    }

    @Test
    fun `without animation a change lands at once too`() {
        show(listOf(real("ada"), real("bo")), animated = false)

        change { lines = listOf(real("bo")) }

        assertEquals(0, count("ada"))
        composeRule.onAllNodesWithText("bo").assertCountEquals(1)
    }

    private companion object {
        const val SETTLED_MS = 2_000L

        // Long enough for the row to be under way, short enough that it has not arrived.
        const val EARLY_MS = 60L
    }
}
