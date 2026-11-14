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
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.test.core.app.ApplicationProvider
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.game.RankOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
 * arrive and leave. Rows move one after another, the place number rolls to its new place and a row that enters the
 * podium gets its medal. With animation off the board has no medals; with reduced motion every change, the final place
 * and the medal included, lands at once.
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

    // The players in order, each at the place its index says.
    private fun ranked(vararg players: String) =
        players.mapIndexed { index, player -> DailyBoardLine.Real(real(player).row, index + 1) }

    private fun show(initial: List<DailyBoardLine>, animated: Boolean = true, reducedMotion: Boolean = false) {
        if (reducedMotion) {
            val context = ApplicationProvider.getApplicationContext<Context>()
            Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        }
        lines = initial
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                val motion = if (animated) DailyBoardStyle.Motion.Animated else DailyBoardStyle.Motion.Static
                DailyBoardList(lines, RankOrder.TimeThenHints, pinned = pinned, style = DailyBoardStyle.defaults(motion = motion))
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

    private fun rank(player: String) = composeRule.onNode(
        hasTestTag("daily_board.row.rank") and hasAnyAncestor(hasTestTag("daily_board.row") and hasAnyDescendant(hasText(player))),
    )

    // The disc is tagged for tests; clearAndSetSemantics on the cell hides it from the merged tree, so read the unmerged one.
    private fun showsMedal(player: String, medal: String): Boolean {
        val cell = composeRule.onNode(
            hasTestTag("daily_board.row.rank") and hasAnyAncestor(hasTestTag("daily_board.row") and hasAnyDescendant(hasText(player))),
            useUnmergedTree = true,
        ).fetchSemanticsNode()
        return hasAnyDescendant(hasTestTag("leaderboard.medal.$medal")).matches(cell)
    }

    private fun showsAnyMedal(player: String) = listOf(GOLD, SILVER, BRONZE).any { showsMedal(player, it) }

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
        composeRule.mainClock.advanceTimeBy(ARRIVAL_MS + EARLY_MS)

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
        composeRule.mainClock.advanceTimeBy(ARRIVAL_MS + EARLY_MS)
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
        // ada is the second row to move: it waits one stagger step before it starts.
        composeRule.mainClock.advanceTimeBy(STAGGER_MS + 130)

        val during = top("ada")
        assertEquals("ada is one row on both days", 1, count("ada"))
        assertTrue("ada at $during, past its old place $start", during > start)
        assertTrue("ink ${ink("ada")} stays the ink $inkBefore of a row not fading", ink("ada") <= inkBefore + 8)
        settle()
        assertTrue("ada at ${top("ada")}, past the $during seen mid-way", top("ada") > during)
    }

    @Test
    fun `a board that stays ranked across a day keeps a shared person's row, which slides under the new title`() {
        var title by mutableStateOf("Alpha · Mon")
        var board by mutableStateOf(DailyBoardState.Ranked(listOf(real("ada").row, real("bo").row, real("cy").row)))
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                DailyBoard(title, RankOrder.TimeThenHints, board, style = DailyBoardStyle.defaults(motion = DailyBoardStyle.Motion.Animated))
            }
        }
        composeRule.mainClock.advanceTimeBy(SETTLED_MS)
        val start = top("ada")
        val inkBefore = ink("ada")

        change {
            title = "Alpha · Sun"
            board = DailyBoardState.Ranked(listOf(real("cy", 9).row, real("ada", 11).row, real("di", 12).row))
        }
        composeRule.mainClock.advanceTimeBy(STAGGER_MS + 130)

        composeRule.onNodeWithTag("daily_board.title").assertTextEquals("Alpha · Sun")
        assertEquals("ada is one row, not one leaving and one arriving", 1, count("ada"))
        assertTrue("ada at ${top("ada")}, past its old place $start", top("ada") > start)
        assertTrue("ink ${ink("ada")} stays the ink $inkBefore of a row not fading", ink("ada") <= inkBefore + 8)
    }

    @Test
    fun `switching day shrinks out a person only on the old day`() {
        show(listOf(real("ada"), real("bo"), real("cy")))
        val full = listHeight()

        switchDay()
        composeRule.mainClock.advanceTimeBy(60)

        assertEquals("bo is still on its way out", 1, count("bo"))
        assertTrue("height ${listHeight()} shrinks from $full", listHeight() < full)
        settle()
        assertEquals(0, count("bo"))
    }

    @Test
    fun `switching day fades in a person only on the new day`() {
        show(listOf(real("ada"), real("bo"), real("cy")))

        switchDay()
        // The board clips the row until it opens, so its name is there to sample from its first visible frame on.
        while (composeRule.onNodeWithText("di").getBoundsInRoot().height == 0.dp) composeRule.mainClock.advanceTimeByFrame()
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

    @Test
    fun `a saved board switching days glides, rolls, arrives and leaves like a live board`() {
        show(ranked("ada", "bo", "cy", "di"))
        val diFrom = top("di")

        change { lines = ranked("di", "ada", "cy", "ei") }
        composeRule.mainClock.advanceTimeBy(EARLY_MS)

        assertTrue("di glides up from $diFrom, it does not jump", top("di") < diFrom)
        assertEquals("bo is still shrinking out", 1, count("bo"))
        rank("di").assertTextEquals("1")
        settle()
        assertEquals("bo is gone once it has shrunk out", 0, count("bo"))
        assertEquals("ei arrived", 1, count("ei"))
        assertTrue("di has its gold once it has moved", showsMedal("di", GOLD))
        assertFalse("ei is fourth: no medal", showsAnyMedal("ei"))
    }

    @Test
    fun `a row waits its turn before it slides, the first one moves at once`() {
        show(listOf(real("ada"), real("bo"), real("cy")))
        val adaFrom = top("ada")
        val cyFrom = top("cy")

        change { lines = listOf(real("cy"), real("ada"), real("bo")) }
        composeRule.mainClock.advanceTimeBy(60)

        assertTrue("cy is first in the new order and has set off", top("cy") < cyFrom)
        assertEquals("ada is second and still waits at its old place", adaFrom, top("ada"))
    }

    @Test
    fun `after a day switch a person on both days is at the new place with its number, and a leaver is gone`() {
        show(ranked("ada", "bo", "cy"))

        change { lines = ranked("cy", "ada", "di") }
        settle()

        rank("ada").assertTextEquals("2")
        rank("cy").assertTextEquals("1")
        rank("di").assertTextEquals("3")
        assertEquals("bo is gone once the glide has settled", 0, count("bo"))
        assertTrue("ada sits under cy", top("ada") > top("cy"))
        assertTrue("and over di", top("ada") < top("di"))
    }

    @Test
    fun `the place number is the final one while it rolls`() {
        show(ranked("ada", "bo", "cy"))

        change { lines = ranked("cy", "bo", "ada") }
        composeRule.mainClock.advanceTimeBy(60)

        rank("ada").assertTextEquals("3")
        rank("cy").assertTextEquals("1")
    }

    @Test
    fun `the podium places are medals once the board has moved, and the others are not`() {
        show(ranked("ada", "bo", "cy", "di"))

        change { lines = ranked("di", "ada", "bo", "cy") }
        settle()

        assertTrue("di entered the podium: gold", showsMedal("di", GOLD))
        assertTrue("ada is second: silver", showsMedal("ada", SILVER))
        assertTrue("bo is third: bronze", showsMedal("bo", BRONZE))
        assertFalse("cy left the podium", showsAnyMedal("cy"))
    }

    @Test
    fun `a row that arrives on the podium has its medal once it has settled`() {
        show(ranked("ada", "bo"))

        change { lines = ranked("ada", "bo", "cy") }
        settle()

        assertTrue(showsMedal("cy", BRONZE))
    }

    @Test
    fun `reduced motion lands the final place and the medal at once`() {
        show(ranked("ada", "bo", "cy", "di"), reducedMotion = true)
        assertTrue("the medals are there from the start", showsMedal("ada", GOLD))

        change { lines = ranked("di", "ada", "bo", "cy") }

        rank("di").assertTextEquals("1")
        assertTrue("di has its gold at once", showsMedal("di", GOLD))
        assertTrue(showsMedal("ada", SILVER))
        assertFalse("cy has no medal at once", showsAnyMedal("cy"))
    }

    @Test
    fun `a board without animation has no medals`() {
        show(ranked("ada", "bo", "cy"), animated = false)

        rank("ada").assertTextEquals("1")
        assertFalse(showsAnyMedal("ada"))
    }

    private companion object {
        // The slowest glide: the last staggered row, after the entrance delay, on a spring that settles in ~1.2 s.
        const val SETTLED_MS = 4_000L

        const val GOLD = "gold"
        const val SILVER = "silver"
        const val BRONZE = "bronze"

        const val STAGGER_MS = 70L
        const val ARRIVAL_MS = 200L

        // Long enough for the row to be under way, short enough that it has not arrived.
        const val EARLY_MS = 60L
    }
}
