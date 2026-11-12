package com.ivandsky.puzzlebox.gameui.board

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
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
 * The glide of a row that crosses the whole board on a day switch, on a paused clock: it travels from its old place to
 * its new one in one direction, never past either, never above the list and so never over what sits above the board,
 * even while other rows open and close around it and the places it travels between are still moving.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class DailyBoardListGlideTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var lines by mutableStateOf<List<DailyBoardLine>>(emptyList())

    private fun real(player: String) =
        DailyBoardLine.Real(DailyBoardRowModel(1, player, 10_000L, 0, false, playerId = player), 1)

    private fun ranked(vararg players: String) = players.mapIndexed { index, player ->
        DailyBoardLine.Real(real(player).row, index + 1)
    }

    // A card of one flat colour right above the board, drawn before it, as the statistics card is on the game page.
    private fun show(initial: List<DailyBoardLine>) {
        lines = initial
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                Column(Modifier.fillMaxWidth()) {
                    Box(Modifier.fillMaxWidth().height(CardHeight).background(CardColor).testTag("card"))
                    DailyBoardList(lines, RankOrder.TimeThenHints, animated = true)
                }
            }
        }
        composeRule.mainClock.advanceTimeBy(SETTLED_MS)
    }

    private fun change(next: List<DailyBoardLine>) {
        composeRule.runOnUiThread { lines = next }
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeByFrame()
    }

    private fun top(player: String): Dp = composeRule.onNodeWithText(player).getBoundsInRoot().top

    private fun listTop(): Dp = composeRule.onNodeWithTag("daily_board.list").getBoundsInRoot().top

    // Whether anything but the card's own colour is drawn inside the card.
    private fun cardCovered(): Boolean {
        val image = composeRule.onNodeWithTag("card").captureToImage().asAndroidBitmap()
        val flat = CardColor.toArgb()
        for (x in 0 until image.width) for (y in 0 until image.height) {
            if (image.getPixel(x, y) != flat) return true
        }
        return false
    }

    /** [player]'s top on each frame of the glide, from the frame the board changed, and whether the card was ever drawn over. */
    private class Glide(val tops: List<Dp>, val listTops: List<Dp>, val coveredCard: Boolean)

    private fun glide(player: String, next: List<DailyBoardLine>): Glide {
        change(next)
        val tops = mutableListOf(top(player))
        val listTops = mutableListOf(listTop())
        var covered = cardCovered()
        repeat(FRAMES) {
            composeRule.mainClock.advanceTimeBy(FRAME_MS)
            tops += top(player)
            listTops += listTop()
            covered = covered || cardCovered()
        }
        return Glide(tops, listTops, covered)
    }

    // Rows that close above the place it glides to move that place up as it settles: it may come back by at most that much.
    private fun assertGlidesDown(glide: Glide) {
        val start = glide.tops.first()
        val end = glide.tops.last()
        assertTrue("it ends below where it began: $start to $end", end > start)
        var highest = start
        glide.tops.forEachIndexed { frame, at ->
            assertTrue("frame $frame fell back from $highest to $at: ${glide.tops}", at >= highest - SettleBack)
            highest = maxOf(highest, at)
        }
        glide.tops.forEachIndexed { frame, at ->
            assertTrue("frame $frame at $at is above the list at ${glide.listTops[frame]}", at >= glide.listTops[frame] - Slack)
            assertTrue("frame $frame at $at is above where it began, $start", at >= start - Slack)
        }
    }

    private fun assertGlidesUp(glide: Glide) {
        val start = glide.tops.first()
        val end = glide.tops.last()
        assertTrue("it ends above where it began: $start to $end", end < start)
        glide.tops.zipWithNext().forEachIndexed { frame, (before, after) ->
            assertTrue("frame $frame went down, $before to $after: ${glide.tops}", after <= before + Slack)
        }
        glide.tops.forEachIndexed { frame, at ->
            assertTrue("frame $frame at $at is above the list at ${glide.listTops[frame]}", at >= glide.listTops[frame] - Slack)
            assertTrue("frame $frame at $at is past its new place $end", at >= end - Slack)
        }
    }

    @Test
    fun `first to last with rows leaving above its new place glides down and never rises over the card`() {
        show(ranked("ada", "bo", "cy", "di"))

        // cy leaves between bo and di, so the places ada travels between close up while it waits and glides.
        assertGlidesDown(glide("ada", ranked("bo", "di", "ada")))
    }

    @Test
    fun `rows are clipped to the board, so nothing is drawn over the card above it`() {
        show(ranked("ada", "bo", "cy", "di"))

        assertFalse("the card was drawn over", glide("ada", ranked("bo", "di", "ada")).coveredCard)
    }

    @Test
    fun `first to last with only the order changing glides down`() {
        show(ranked("ada", "bo", "cy"))

        assertGlidesDown(glide("ada", ranked("bo", "cy", "ada")))
    }

    @Test
    fun `first to last with rows arriving glides down`() {
        show(ranked("ada", "bo"))

        assertGlidesDown(glide("ada", ranked("bo", "di", "ee", "ada")))
    }

    @Test
    fun `last to first glides up and stays inside the list`() {
        show(ranked("bo", "cy", "di", "ada"))

        val glide = glide("ada", ranked("ada", "bo", "di", "ee"))

        assertGlidesUp(glide)
        assertTrue("nothing was drawn over the card above the board", !glide.coveredCard)
    }

    @Test
    fun `the row ends at its new place`() {
        show(ranked("ada", "bo", "cy", "di"))
        change(ranked("bo", "di", "ada"))
        composeRule.mainClock.advanceTimeBy(SETTLED_MS)

        assertTrue("ada sits under di", top("ada") > top("di"))
        assertEquals("cy is gone", 0, composeRule.onAllNodesWithText("cy").fetchSemanticsNodes().size)
    }

    private companion object {
        val CardColor = Color.Red
        val CardHeight = 80.dp
        const val SETTLED_MS = 4_000L
        const val FRAME_MS = 16L
        const val FRAMES = 100

        // Float noise of the layout rounding, in dp.
        val Slack = 0.6.dp

        // Closing rows pull the place the glide ends at upwards while it settles, by a fraction of a row.
        val SettleBack = 12.dp
    }
}
