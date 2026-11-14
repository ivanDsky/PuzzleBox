package com.ivandsky.puzzlebox.gameui.board

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
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
 * What a row hides: every kind of row is drawn opaque, so rows that overlap while one glides past the others never
 * show through each other, and a row on its way is drawn over the rows it crosses, not under them.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class BoardRowOpacityTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var lines by mutableStateOf<List<DailyBoardLine>>(emptyList())

    private fun real(player: String, self: Boolean = false) =
        DailyBoardRowModel(1, player, 10_000L, 0, self, playerId = player)

    private fun ranked(vararg rows: DailyBoardRowModel) =
        rows.mapIndexed { index, row -> DailyBoardLine.Real(row, index + 1) }

    // The list over a flat colour that no row has, so a row that lets it through shows it.
    private fun show(initial: List<DailyBoardLine>) {
        lines = initial
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                Box(Modifier.fillMaxWidth().background(Behind)) {
                    DailyBoardList(lines, RankOrder.TimeThenHints, style = DailyBoardStyle.defaults(motion = DailyBoardStyle.Motion.Animated))
                }
            }
        }
        composeRule.mainClock.advanceTimeBy(SETTLED_MS)
    }

    private fun SemanticsNodeInteraction.bounds(): Rect = fetchSemanticsNode().boundsInRoot

    // A pixel in the middle of the row between its name and its time, where a row has no content of its own.
    private fun SemanticsNodeInteraction.emptyPixel(): Int {
        val image = captureToImage().asAndroidBitmap()
        return image.getPixel(image.width * 11 / 20, image.height / 2)
    }

    @Test
    fun `every kind of row is drawn opaque`() {
        val ghost = DailyBoardLine.Ghost(GhostRowModel("ref-gia", "Gia", 20_000L, hintFree = true), 3)
        show(ranked(real("ada", self = true), real("bo")) + ghost)

        val pixels = listOf("daily_board.row.self", "daily_board.row", "daily_board.ghost")
            .associateWith { composeRule.onNodeWithTag(it).emptyPixel() }

        pixels.forEach { (row, pixel) -> assertTrue("$row lets what is behind it show through", pixel != Behind.toArgb()) }
    }

    @Test
    fun `a row that glides past another is drawn over it`() {
        // The mover goes up from last to first, so it comes before the rows it passes and is drawn under them unless it is lifted.
        show(ranked(real("Bo", self = true), real("Cy"), real(Mover)))
        composeRule.runOnUiThread { lines = ranked(real(Mover), real("Bo", self = true), real("Cy")) }
        composeRule.waitForIdle()

        var checked = 0
        var covered = 0
        val least = with(composeRule.density) { LeastOverlap.toPx() }
        repeat(FRAMES) {
            composeRule.mainClock.advanceTimeBy(FRAME_MS)
            val name = composeRule.onNodeWithText(Mover).bounds()
            val crossed = composeRule.onNodeWithTag("daily_board.row.self").bounds()
            val crossedName = composeRule.onNodeWithText("Bo").bounds()
            // The part of the mover's long name that is over the crossed row and clear of the crossed row's own short
            // name: nothing but the mover can have drawn ink there.
            val over = Rect(crossedName.right, maxOf(name.top, crossed.top), name.right, minOf(name.bottom, crossed.bottom))
            if (over.width > 0 && over.height >= least) {
                checked++
                if (inkIn(over) == 0) covered++
            }
        }

        assertTrue("the mover never crossed bo's row", checked > 0)
        assertEquals("the mover's name was covered by the row it crossed on $covered of $checked frames", 0, covered)
    }

    private fun inkIn(area: Rect): Int {
        val list = composeRule.onNodeWithTag("daily_board.list")
        val origin = list.bounds()
        val image = list.captureToImage().asAndroidBitmap()
        var ink = 0
        for (x in (area.left - origin.left).toInt() until (area.right - origin.left).toInt()) {
            for (y in (area.top - origin.top).toInt() until (area.bottom - origin.top).toInt()) {
                if (x in 0 until image.width && y in 0 until image.height && isDark(image.getPixel(x, y))) ink++
            }
        }
        return ink
    }

    private fun isDark(pixel: Int): Boolean {
        val color = Color(pixel)
        return color.red + color.green + color.blue < DarkSum
    }

    private companion object {
        val Behind = Color.Yellow
        const val SETTLED_MS = 4_000L
        const val FRAME_MS = 16L
        const val FRAMES = 100
        const val DarkSum = 1.2f
        const val Mover = "Adalyn Longname Ferguson"
        val LeastOverlap = 16.dp
    }
}
