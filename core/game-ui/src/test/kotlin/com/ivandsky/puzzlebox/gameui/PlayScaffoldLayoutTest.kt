package com.ivandsky.puzzlebox.gameui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import com.ivandsky.puzzlebox.designsystem.AccentSlot
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.Spacing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The play scaffold (A15; nav-rethink B5, B6): the board keeps to its device class's cap ([boardCapFor]) and
 * the control row sits directly under it in a single pane, or in a column beside it on a landscape tablet,
 * whose side pane narrows to ~30% and hosts the route's slot.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w1848dp-h1000dp")
class PlayScaffoldLayoutTest {

    @get:Rule
    val composeRule = createComposeRule()

    private object Undo : PlayUndo {
        override val canUndo = true
        override val canRedo = false
        override fun undo() = Unit
        override fun redo() = Unit
    }

    private object Hints : PlayHints {
        override val hintsUsed = 0
        override val hintEnabled = true
        override val hintCaption: String? = null
        override fun requestHint() = Unit
    }

    private fun setScreen(
        layout: PlayLayout,
        width: Int,
        height: Int,
        boardSize: Dp? = null,
        gridColumns: Int? = null,
        hints: PlayHints = Hints,
        slot: @Composable ColumnScope.() -> Unit = { Text("stats", Modifier.testTag("slot")) },
    ) {
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                AdaptivePlayScaffold(
                    layout = layout,
                    gridColumns = gridColumns,
                    modifier = Modifier.requiredSize(width.dp, height.dp),
                    // Honours the cap the way Tango's and Zip's boards do.
                    board = { paneCap ->
                        PlayBoardPane(Modifier.testTag("pane").padding(horizontal = Spacing.lg)) {
                            // A fixed size stands for a short board, smaller than its pane.
                            val size = boardSize?.let { Modifier.size(it) }
                                ?: Modifier.widthIn(max = paneCap).heightIn(max = paneCap).aspectRatio(1f)
                            Box(size.testTag("board"))
                        }
                    },
                    side = { PlayChrome(clock = null, hints = Hints) },
                    actions = { PlayActionBar(Undo, hints, clear = PlayClear {}, accent = AccentSlot.Teal) },
                    sidePanel = slot,
                )
            }
        }
    }

    private fun bounds(tag: String): DpRect = composeRule.onNodeWithTag(tag).getUnclippedBoundsInRoot()

    private val controlTags = listOf("play.undo", "play.redo", "play.hint", "play.clear")

    /** The DoD shape: a board [side] wide, the four controls in order in a row directly under it, as wide as it. */
    private fun assertRowDirectlyUnderBoard(side: Dp) {
        val board = bounds("board")
        val bar = bounds("play.action_bar")
        val controls = controlTags.map(::bounds)

        assertEquals("board width", side.value, board.width.value, 0.5f)
        assertEquals("row $bar directly under board $board", (board.bottom + PlayBoardInset).value, bar.top.value, 0.5f)
        assertEquals("row $bar starts with board $board", board.left.value, bar.left.value, 0.5f)
        assertEquals("row $bar ends with board $board", board.right.value, bar.right.value, 0.5f)
        controls.zipWithNext { left, right -> assertTrue("$left before $right", left.right <= right.left) }
        controls.forEach { control ->
            assertEquals("$control in the row $bar", bar.top.value, control.top.value, 0.5f)
            assertTrue("touch target $control", control.width >= 48.dp && control.height >= 48.dp)
        }
        composeRule.onNodeWithTag("play.toolbar").assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "w360dp-h800dp")
    fun `a phone shrinks the board to the pane's margins with the control row directly under it`() {
        setScreen(PlayLayout.SinglePane, 360, 800, gridColumns = 6)

        assertRowDirectlyUnderBoard(side = 360.dp - PlayBoardInset * 2)
    }

    @Test
    @Config(qualifiers = "w800dp-h1280dp")
    fun `a 6 by 6 board on a portrait tablet is 576 dp`() {
        setScreen(PlayLayout.SinglePane, 800, 1280, gridColumns = 6)

        assertRowDirectlyUnderBoard(side = 576.dp)
    }

    @Test
    @Config(qualifiers = "w800dp-h1280dp")
    fun `a 9 by 9 board on a portrait tablet is 640 dp`() {
        setScreen(PlayLayout.SinglePane, 800, 1280, gridColumns = 9)

        assertRowDirectlyUnderBoard(side = 640.dp)
    }

    @Test
    @Config(qualifiers = "w800dp-h1280dp")
    fun `a portrait tablet with no grid columns keeps the class cap`() {
        setScreen(PlayLayout.SinglePane, 800, 1280)

        assertRowDirectlyUnderBoard(side = 640.dp)
    }

    @Test
    @Config(qualifiers = "w1280dp-h800dp")
    fun `a 6 by 6 board on a landscape tablet is 576 dp with the controls in the side column`() {
        setScreen(PlayLayout.TwoPane, 1280, 800, gridColumns = 6)
        val board = bounds("board")

        assertEquals("board width", 576f, board.width.value, 0.5f)
        controlTags.map(::bounds).forEach { control ->
            assertEquals("$control beside board $board", (board.right + PlayBoardInset).value, control.left.value, 0.5f)
        }
    }

    @Test
    @Config(qualifiers = "w1280dp-h800dp")
    fun `a 9 by 9 board on a landscape tablet is 640 dp with the controls in the side column`() {
        setScreen(PlayLayout.TwoPane, 1280, 800, gridColumns = 9)
        val board = bounds("board")

        assertEquals("board width", 640f, board.width.value, 0.5f)
        controlTags.map(::bounds).forEach { control ->
            assertEquals("$control beside board $board", (board.right + PlayBoardInset).value, control.left.value, 0.5f)
        }
    }

    @Test
    @Config(qualifiers = "w1280dp-h800dp")
    fun `the side column stacks the controls to the right of the board on a landscape tablet`() {
        setScreen(PlayLayout.TwoPane, 1280, 800)
        val board = bounds("board")
        val controls = controlTags.map(::bounds)

        assertEquals("board width", 640f, board.width.value, 0.5f)
        controls.zipWithNext { above, below -> assertTrue("$above over $below", above.bottom <= below.top) }
        controls.forEach { control ->
            assertEquals("$control beside board $board", (board.right + PlayBoardInset).value, control.left.value, 0.5f)
            assertTrue("$control within the board's height $board", control.top >= board.top && control.bottom <= board.bottom)
            assertTrue("$control inside the board pane", control.right <= bounds("play.side_pane").left)
            assertTrue("touch target $control", control.width >= 48.dp && control.height >= 48.dp)
        }
        assertEquals("one column width", 1, controls.map { it.width }.distinct().size)
        assertTrue("side pane ≈30%", kotlin.math.abs(bounds("play.side_pane").width.value / 1280f - 0.3f) < 0.01f)
        composeRule.onNode(hasTestTag("slot") and hasAnyAncestor(hasTestTag("play.side_pane"))).assertExists()
    }

    @Test
    @Config(qualifiers = "w1280dp-h800dp")
    fun `the side column keeps the action bar's disabled rules`() {
        setScreen(PlayLayout.TwoPane, 1280, 800)

        composeRule.onNodeWithTag("play.undo").assertIsEnabled()
        composeRule.onNodeWithTag("play.redo").assertIsNotEnabled()
    }

    // Native graphics: the default mode does not measure text, so a longer label would not widen anything.
    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Config(qualifiers = "w1280dp-h800dp")
    fun `a growing hint count does not move the board beside the side column`() {
        val hints = object : PlayHints {
            override var hintsUsed by mutableIntStateOf(0)
            override val hintEnabled = true
            override val hintCaption: String? = null
            override fun requestHint() = Unit
        }
        setScreen(PlayLayout.TwoPane, 1280, 800, hints = hints)
        val before = bounds("board")

        hints.hintsUsed = 120
        composeRule.waitForIdle()

        assertEquals(before, bounds("board"))
    }

    @Test
    @Config(qualifiers = "w360dp-h800dp")
    fun `a phone moves the caption below the control row`() {
        setScreen(PlayLayout.SinglePane, 360, 800)
        val bar = bounds("play.action_bar")
        val caption = bounds("play.caption_slot")

        assertTrue("caption $caption below row $bar", caption.top >= bar.bottom)
        assertTrue("caption $caption close under row $bar", caption.top - bar.bottom <= Spacing.xl)
    }

    @Test
    @Config(qualifiers = "w800dp-h1280dp")
    fun `a portrait tablet lines the caption up with its capped board`() {
        setScreen(PlayLayout.SinglePane, 800, 1280)
        val board = bounds("board")
        val caption = bounds("play.caption_slot")

        assertEquals("caption $caption starts with board $board", board.left.value, caption.left.value, 0.5f)
        assertEquals("caption $caption ends with board $board", board.right.value, caption.right.value, 0.5f)
    }

    @Test
    @Config(qualifiers = "w800dp-h1280dp")
    fun `the caption lines up with a cell capped board`() {
        setScreen(PlayLayout.SinglePane, 800, 1280, gridColumns = 6)
        val board = bounds("board")
        val caption = bounds("play.caption_slot")

        assertEquals("caption $caption starts with board $board", board.left.value, caption.left.value, 0.5f)
        assertEquals("caption $caption ends with board $board", board.right.value, caption.right.value, 0.5f)
    }

    @Test
    @Config(qualifiers = "w360dp-h800dp")
    fun `a board shorter than its cap leaves the control row where a full board puts it`() {
        setScreen(PlayLayout.SinglePane, 360, 800, boardSize = 200.dp)
        val pane = bounds("pane")
        val board = bounds("board")

        assertEquals("the pane keeps the full board's square", (360.dp - PlayBoardInset * 2 + PlayBoardInset * 2).value, pane.height.value, 0.5f)
        assertEquals("row directly under the pane", pane.bottom.value, bounds("play.action_bar").top.value, 0.5f)
        assertEquals("board centred in the pane", (board.top - pane.top).value, (pane.bottom - board.bottom).value, 1f)
    }

    @Test
    fun `a slot taller than the side pane scrolls under the game's chrome, never over it`() {
        setScreen(PlayLayout.TwoPane, 1848, 1000, slot = { Box(Modifier.height(5000.dp).testTag("slot")) })
        val pane = composeRule.onNodeWithTag("play.side_pane").getUnclippedBoundsInRoot()
        val chrome = composeRule.onNodeWithTag("play.side_panel").getUnclippedBoundsInRoot()
        val slot = composeRule.onNodeWithTag("play.side_slot").getUnclippedBoundsInRoot()

        assertTrue("chrome $chrome keeps its height", chrome.height > 0.dp)
        assertTrue("slot $slot under chrome $chrome", slot.top >= chrome.bottom)
        assertTrue("slot $slot bounded by pane $pane", slot.bottom <= pane.bottom)
    }

    @Test
    @Config(qualifiers = "w360dp-h800dp")
    fun `a phone shows no slot`() {
        setScreen(PlayLayout.SinglePane, 360, 800)

        composeRule.onNodeWithTag("slot").assertDoesNotExist()
    }
}
