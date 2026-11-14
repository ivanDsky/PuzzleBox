package com.ivandsky.puzzlebox.gameui.board

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.gameui.board.DailyBoardStyle.Motion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * One style says how a board looks and moves, and each choice in it reaches every way the board draws its rows: at
 * rest, with motion, between two boards and in the dock.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class DailyBoardStyleTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val order = RankOrder.TimeThenHints
    private var motion by mutableStateOf<Motion>(Motion.Static)
    private var filledRows by mutableStateOf(false)

    // Twenty rows. The first carries a mark, and so does the viewer's own, the tenth, which is under a 200 dp viewport.
    private val tall = BoardFace(
        "Zip · today",
        order,
        DailyBoardState.Ranked(
            (1..20).map { rank ->
                val self = rank == 10
                DailyBoardRowModel(rank, if (self) "You" else "Player $rank", rank * 10_000L, 0, self, playerId = "p$rank", unofficial = rank == 1 || self)
            },
        ),
    )

    private fun face(title: String, vararg names: String) = BoardFace(
        title,
        order,
        DailyBoardState.Ranked(names.mapIndexed { index, name -> DailyBoardRowModel(index + 1, name, 60_000L, 0, isSelf = false, playerId = name) }),
    )

    private fun show(board: BoardFace = tall, inlineMarks: Boolean = true, dock: Boolean = false) {
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                Box(Modifier.height(200.dp).verticalScroll(rememberScrollState())) {
                    DailyBoard(
                        board.title,
                        board.order,
                        board.state,
                        style = DailyBoardStyle.defaults(motion = motion, dock = dock, inlineMarks = inlineMarks),
                    )
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun move(to: Motion) {
        motion = to
        composeRule.waitForIdle()
    }

    // Only another player's row is tagged so: the viewer's own, and its copy in the dock, have a tag of their own.
    private val inFirstRow = hasAnyAncestor(hasTestTag("daily_board.row"))
    private val inDock = hasAnyAncestor(hasTestTag("leaderboard.dock"))
    private val mark = hasTestTag("daily_board.row.mark")

    // The dock, and a list between two boards, say nothing to assistive tech: their rows are only in the unmerged tree.
    private fun bounds(matcher: SemanticsMatcher) = composeRule.onNode(matcher, useUnmergedTree = true).getBoundsInRoot()

    private fun assertMarkBeside(name: SemanticsMatcher, mark: SemanticsMatcher) {
        val text = bounds(name)
        val chip = bounds(mark)
        assertTrue("the mark $chip is beside the name $text", chip.left >= text.right && chip.top >= text.top && chip.bottom <= text.bottom)
    }

    @Test
    fun `the defaults are a plain board at rest on the theme's background`() {
        var style: DailyBoardStyle? = null
        var background = Color.Unspecified
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                style = DailyBoardStyle.defaults()
                background = MaterialTheme.colorScheme.background
            }
        }
        composeRule.waitForIdle()

        val plain = DailyBoardStyle(Motion.Static, background, filledRows = false, medals = false, dock = false, morphText = false, inlineMarks = false)
        assertEquals(plain, style)
    }

    @Test
    fun `a board that does not ask for inline marks keeps a row's mark under its name`() {
        show(inlineMarks = false)

        val name = bounds(hasText("Player 1"))
        val line = bounds(mark and inFirstRow)
        assertTrue("the mark $line is under the name $name", line.top >= name.bottom && line.left == name.left)
    }

    @Test
    fun `inline marks reach the rows of a board at rest`() {
        show()

        assertMarkBeside(hasText("Player 1"), mark and inFirstRow)
    }

    @Test
    fun `inline marks reach the rows of a board with motion`() {
        motion = Motion.Animated
        show()

        assertMarkBeside(hasText("Player 1"), mark and inFirstRow)
    }

    @Test
    fun `inline marks reach the rows of a board between two boards`() {
        motion = Motion.Morphing(BoardMorph(tall, tall.copy(title = "Tango · today")) { 0.5f })
        show()

        assertMarkBeside(hasText("Player 1"), mark and inFirstRow)
    }

    @Test
    fun `inline marks reach the docked row, at rest and between two boards`() {
        motion = Motion.Animated
        show(dock = true)

        assertMarkBeside(hasText("You") and inDock, mark and inDock)

        move(Motion.Morphing(BoardMorph(tall, tall.copy(title = "Tango · today")) { 0.5f }))

        assertMarkBeside(hasText("You") and inDock, mark and inDock)
    }

    // A pixel in the middle of the first row, between its name and its time, where a row has no content of its own.
    private fun rowPixel(): Int {
        val image = composeRule.onAllNodesWithTag("daily_board.row")[0].captureToImage().asAndroidBitmap()
        return image.getPixel(image.width * 11 / 20, image.height / 2)
    }

    @Test
    fun `a board at rest fills its rows with the backdrop only when the style fills them`() {
        val board = face("Zip · today", "Ann", "Bea")
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                Box(Modifier.fillMaxWidth().background(Behind)) {
                    DailyBoard(board.title, board.order, board.state, style = DailyBoardStyle.defaults(backdrop = Backdrop, filledRows = filledRows))
                }
            }
        }

        assertEquals("a row that is not filled lets what is behind it through", Behind.toArgb(), rowPixel())

        filledRows = true
        composeRule.waitForIdle()

        assertEquals(Backdrop.toArgb(), rowPixel())
    }

    @Test
    fun `a morphing board draws the two boards as one list, an animated one its own`() {
        val leaving = face("Zip · today", "Ann", "Bea")
        val arriving = face("Tango · today", "Ann", "Dee")
        motion = Motion.Morphing(BoardMorph(leaving, arriving) { 0.5f })
        show(arriving)

        listOf("Ann", "Bea", "Dee").forEach { composeRule.onAllNodesWithText(it, useUnmergedTree = true).assertCountEquals(1) }

        move(Motion.Animated)

        composeRule.onAllNodesWithText("Bea", useUnmergedTree = true).assertCountEquals(0)
        listOf("Ann", "Dee").forEach { composeRule.onAllNodesWithText(it).assertCountEquals(1) }
    }

    @Test
    fun `a list alone is never between two boards, so it moves as an animated one`() {
        val own = face("Zip · today", "Ann", "Bea")
        val other = face("Tango · today", "Cal", "Dee")
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                DailyBoardList(
                    boardLines(own.state, own.ghosts, own.order),
                    own.order,
                    style = DailyBoardStyle.defaults(motion = Motion.Morphing(BoardMorph(own, other) { 0.5f })),
                )
            }
        }
        composeRule.waitForIdle()

        listOf("Ann", "Bea").forEach { composeRule.onAllNodesWithText(it).assertCountEquals(1) }
        composeRule.onAllNodesWithText("Dee", useUnmergedTree = true).assertCountEquals(0)
    }

    private companion object {
        val Behind = Color.Yellow
        val Backdrop = Color.Cyan
    }
}
