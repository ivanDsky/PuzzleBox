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
 * A15 (no tuning): on a landscape tablet the controls sit in a toolbar above a board that
 * fills its pane, and the side pane narrows to ~30% and hosts the route's slot; a phone keeps
 * the action bar under the caption. Under a [PlayTuning] (nav-rethink B5, B6) the board keeps
 * to its device class's cap and the control row sits directly under it in every window, or in
 * a column beside it on a landscape tablet set to [LandscapeControls.SideColumn].
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

    /** A cap per device class, each below what its window offers, so the cap is what binds. */
    private val caps = PlayTuning(
        phoneCap = 312.dp,
        tabletPortraitCap = 448.dp,
        tabletLandscapeCap = 480.dp,
        landscapeControls = LandscapeControls.UnderBoard,
    )

    private fun setScreen(
        layout: PlayLayout,
        width: Int,
        height: Int,
        boardSize: Dp? = null,
        tuning: PlayTuning? = null,
        gridColumns: Int? = null,
        hints: PlayHints = Hints,
        slot: @Composable ColumnScope.() -> Unit = { Text("stats", Modifier.testTag("slot")) },
    ) {
        // Clear only under a tuning: today's cases keep the three-button bar they were written on.
        val clear = tuning?.let { PlayClear {} }
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                CompositionLocalProvider(LocalPlayTuning provides tuning) {
                    AdaptivePlayScaffold(
                        layout = layout,
                        gridColumns = gridColumns,
                        modifier = Modifier.requiredSize(width.dp, height.dp),
                        // Honours the cap the way Tango's and Zip's boards do.
                        board = { paneCap ->
                            val cap = paneCap ?: PlayBoardMaxWidth
                            PlayBoardPane(Modifier.testTag("pane").padding(horizontal = Spacing.lg)) {
                                // A fixed size stands for a short board, smaller than its pane.
                                val size = boardSize?.let { Modifier.size(it) }
                                    ?: Modifier.widthIn(max = cap).heightIn(max = cap).aspectRatio(1f)
                                Box(size.testTag("board"))
                            }
                        },
                        side = { PlayChrome(clock = null, hints = Hints) },
                        actions = { PlayActionBar(Undo, hints, clear = clear, accent = AccentSlot.Teal) },
                        sidePanel = slot,
                    )
                }
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
    fun `a tuned phone keeps the board to its cap with the control row directly under it`() {
        setScreen(PlayLayout.SinglePane, 360, 800, tuning = caps)

        assertRowDirectlyUnderBoard(side = 312.dp)
    }

    @Test
    @Config(qualifiers = "w800dp-h1280dp")
    fun `a tuned portrait tablet keeps the board to its cap with the control row directly under it`() {
        setScreen(PlayLayout.SinglePane, 800, 1280, tuning = caps)

        assertRowDirectlyUnderBoard(side = 448.dp)
    }

    @Test
    @Config(qualifiers = "w1280dp-h800dp")
    fun `a tuned landscape tablet keeps the board to its cap with the control row directly under it`() {
        setScreen(PlayLayout.TwoPane, 1280, 800, tuning = caps)

        assertRowDirectlyUnderBoard(side = 480.dp)
        assertTrue("row inside the board pane", bounds("play.action_bar").right <= bounds("play.side_pane").left)
    }

    @Test
    @Config(qualifiers = "w1280dp-h800dp")
    fun `the side column stacks the same controls to the right of the board on a landscape tablet`() {
        setScreen(PlayLayout.TwoPane, 1280, 800, tuning = caps.copy(landscapeControls = LandscapeControls.SideColumn))
        val board = bounds("board")
        val controls = controlTags.map(::bounds)

        assertEquals("board width", 480f, board.width.value, 0.5f)
        controls.zipWithNext { above, below -> assertTrue("$above over $below", above.bottom <= below.top) }
        controls.forEach { control ->
            assertEquals("$control beside board $board", (board.right + PlayBoardInset).value, control.left.value, 0.5f)
            assertTrue("$control within the board's height $board", control.top >= board.top && control.bottom <= board.bottom)
            assertTrue("$control inside the board pane", control.right <= bounds("play.side_pane").left)
            assertTrue("touch target $control", control.width >= 48.dp && control.height >= 48.dp)
        }
        assertEquals("one column width", 1, controls.map { it.width }.distinct().size)
    }

    @Test
    @Config(qualifiers = "w1280dp-h800dp")
    fun `the side column keeps the action bar's disabled rules`() {
        setScreen(PlayLayout.TwoPane, 1280, 800, tuning = caps.copy(landscapeControls = LandscapeControls.SideColumn))

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
        val tuning = caps.copy(landscapeControls = LandscapeControls.SideColumn)
        setScreen(PlayLayout.TwoPane, 1280, 800, tuning = tuning, hints = hints)
        val before = bounds("board")

        hints.hintsUsed = 120
        composeRule.waitForIdle()

        assertEquals(before, bounds("board"))
    }

    @Test
    @Config(qualifiers = "w360dp-h800dp")
    fun `a phone keeps the control row under the board when the tuning says side column`() {
        setScreen(PlayLayout.SinglePane, 360, 800, tuning = caps.copy(landscapeControls = LandscapeControls.SideColumn))

        assertRowDirectlyUnderBoard(side = 312.dp)
    }

    @Test
    @Config(qualifiers = "w360dp-h800dp")
    fun `a tuned phone moves the caption below the control row`() {
        setScreen(PlayLayout.SinglePane, 360, 800, tuning = caps)
        val bar = bounds("play.action_bar")
        val caption = bounds("play.caption_slot")

        assertTrue("caption $caption below row $bar", caption.top >= bar.bottom)
        assertTrue("caption $caption close under row $bar", caption.top - bar.bottom <= Spacing.xl)
    }

    @Test
    @Config(qualifiers = "w800dp-h1280dp")
    fun `a tuned portrait tablet lines the caption up with its capped board`() {
        setScreen(PlayLayout.SinglePane, 800, 1280, tuning = caps)
        val board = bounds("board")
        val caption = bounds("play.caption_slot")

        assertEquals("caption $caption starts with board $board", board.left.value, caption.left.value, 0.5f)
        assertEquals("caption $caption ends with board $board", board.right.value, caption.right.value, 0.5f)
    }

    @Test
    @Config(qualifiers = "w360dp-h800dp")
    fun `a cap wider than the phone shrinks the board to the pane's margins`() {
        setScreen(PlayLayout.SinglePane, 360, 800, tuning = PlayTuning.Starting)

        assertRowDirectlyUnderBoard(side = 360.dp - PlayBoardInset * 2)
    }

    /** The user's choices (ticket 56): 552 / 600 / 600 dp caps, a 72 dp tablet cell cap, controls in the side column. */
    private val shipped = ShippedPlayTuningSource.tuning.value!!

    @Test
    @Config(qualifiers = "w360dp-h800dp")
    fun `the shipped tuning leaves the phone board as wide as the starting one`() {
        setScreen(PlayLayout.SinglePane, 360, 800, tuning = shipped, gridColumns = 6)

        assertEquals(PlayTuning.Starting.phoneCap, shipped.phoneCap)
        assertRowDirectlyUnderBoard(side = 360.dp - PlayBoardInset * 2)
    }

    @Test
    @Config(qualifiers = "w360dp-h800dp")
    fun `the cell cap is off on the phone`() {
        // 40 dp x 6 = 240 dp would bind below the 312 dp cap if it applied.
        setScreen(PlayLayout.SinglePane, 360, 800, tuning = caps.copy(tabletCellCap = 40.dp), gridColumns = 6)

        assertRowDirectlyUnderBoard(side = 312.dp)
    }

    @Test
    @Config(qualifiers = "w800dp-h1280dp")
    fun `a 6 by 6 board on a shipped portrait tablet is 432 dp`() {
        setScreen(PlayLayout.SinglePane, 800, 1280, tuning = shipped, gridColumns = 6)

        assertRowDirectlyUnderBoard(side = 432.dp)
    }

    @Test
    @Config(qualifiers = "w800dp-h1280dp")
    fun `a 9 by 9 board on a shipped portrait tablet is 600 dp`() {
        setScreen(PlayLayout.SinglePane, 800, 1280, tuning = shipped, gridColumns = 9)

        assertRowDirectlyUnderBoard(side = 600.dp)
    }

    @Test
    @Config(qualifiers = "w800dp-h1280dp")
    fun `a portrait tablet with no grid columns keeps the class cap`() {
        setScreen(PlayLayout.SinglePane, 800, 1280, tuning = shipped)

        assertRowDirectlyUnderBoard(side = 600.dp)
    }

    @Test
    @Config(qualifiers = "w800dp-h1280dp")
    fun `the caption lines up with a cell capped board`() {
        setScreen(PlayLayout.SinglePane, 800, 1280, tuning = shipped, gridColumns = 6)
        val board = bounds("board")
        val caption = bounds("play.caption_slot")

        assertEquals("caption $caption starts with board $board", board.left.value, caption.left.value, 0.5f)
        assertEquals("caption $caption ends with board $board", board.right.value, caption.right.value, 0.5f)
    }

    @Test
    @Config(qualifiers = "w1280dp-h800dp")
    fun `a 6 by 6 board on a shipped landscape tablet is 432 dp with the controls in the side column`() {
        setScreen(PlayLayout.TwoPane, 1280, 800, tuning = shipped, gridColumns = 6)
        val board = bounds("board")

        assertEquals("board width", 432f, board.width.value, 0.5f)
        controlTags.map(::bounds).forEach { control ->
            assertEquals("$control beside board $board", (board.right + PlayBoardInset).value, control.left.value, 0.5f)
        }
    }

    @Test
    @Config(qualifiers = "w1280dp-h800dp")
    fun `a 9 by 9 board on a shipped landscape tablet is 600 dp with the controls in the side column`() {
        setScreen(PlayLayout.TwoPane, 1280, 800, tuning = shipped, gridColumns = 9)
        val board = bounds("board")

        assertEquals("board width", 600f, board.width.value, 0.5f)
        controlTags.map(::bounds).forEach { control ->
            assertEquals("$control beside board $board", (board.right + PlayBoardInset).value, control.left.value, 0.5f)
        }
    }

    @Test
    @Config(qualifiers = "w1280dp-h800dp")
    fun `a cell cap applies to a landscape tablet that has no class cap`() {
        setScreen(PlayLayout.TwoPane, 1280, 800, tuning = PlayTuning.Starting.copy(tabletCellCap = 64.dp), gridColumns = 6)

        assertRowDirectlyUnderBoard(side = 384.dp)
    }

    @Test
    @Config(qualifiers = "w1280dp-h800dp")
    fun `a landscape tablet with no cap fills the pane's height above its control row`() {
        setScreen(PlayLayout.TwoPane, 1280, 800, tuning = PlayTuning.Starting)
        val board = bounds("board")
        val bar = bounds("play.action_bar")

        assertRowDirectlyUnderBoard(side = board.width)
        assertEquals("board under the pane's top inset", PlayBoardInset.value, board.top.value, 0.5f)
        assertEquals("row on the pane's bottom margin", (800.dp - Spacing.sm).value, bar.bottom.value, 0.5f)
    }

    @Test
    @Config(qualifiers = "w360dp-h800dp")
    fun `a board shorter than its cap leaves the control row where a full board puts it`() {
        setScreen(PlayLayout.SinglePane, 360, 800, boardSize = 200.dp, tuning = caps)
        val pane = bounds("pane")
        val board = bounds("board")

        assertEquals("the pane keeps the cap's square", (312.dp + PlayBoardInset * 2).value, pane.height.value, 0.5f)
        assertEquals("row directly under the pane", pane.bottom.value, bounds("play.action_bar").top.value, 0.5f)
        assertEquals("board centred in the pane", (board.top - pane.top).value, (pane.bottom - board.bottom).value, 1f)
    }

    @Test
    fun `a landscape tablet puts the toolbar above a board that fills its pane`() {
        setScreen(PlayLayout.TwoPane, 1848, 1000)
        val board = composeRule.onNodeWithTag("board").getUnclippedBoundsInRoot()
        val toolbar = composeRule.onNodeWithTag("play.action_bar").getUnclippedBoundsInRoot()
        val side = composeRule.onNodeWithTag("play.side_pane").getUnclippedBoundsInRoot()

        assertTrue("board width ${board.width}", board.width > 700.dp)
        assertTrue("toolbar $toolbar above board $board", toolbar.bottom <= board.top)
        assertTrue("toolbar $toolbar spans the board's columns $board", toolbar.left >= board.left && toolbar.right <= board.right)
        assertEquals("side pane ≈30%", 0.3f, side.width.value / 1848f, 0.01f)
        composeRule.onNode(hasTestTag("slot") and hasAnyAncestor(hasTestTag("play.side_pane"))).assertExists()
    }

    @Test
    fun `a short board sits centred between the top chrome and the bottom controls on a phone`() {
        setScreen(PlayLayout.SinglePane, 360, 800, boardSize = 200.dp)
        val board = composeRule.onNodeWithTag("board").getUnclippedBoundsInRoot()
        val pane = composeRule.onNodeWithTag("pane").getUnclippedBoundsInRoot()

        val above = board.top - pane.top
        val below = pane.bottom - board.bottom
        assertTrue("room to centre in: above $above below $below", above > PlayBoardInset)
        assertEquals("equal space above and below the board", above.value, below.value, 1f)
    }

    @Test
    fun `a short board sits centred under the toolbar in the two-pane board pane`() {
        setScreen(PlayLayout.TwoPane, 1848, 1000, boardSize = 200.dp)
        val board = composeRule.onNodeWithTag("board").getUnclippedBoundsInRoot()
        val toolbar = composeRule.onNodeWithTag("play.toolbar").getUnclippedBoundsInRoot()

        val above = board.top - toolbar.bottom
        val below = 1000.dp - board.bottom
        assertTrue("room to centre in: above $above below $below", above > PlayBoardInset)
        assertEquals("equal space above and below the board", above.value, below.value, 1f)
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
    fun `a phone keeps the action bar under the caption and no slot`() {
        setScreen(PlayLayout.SinglePane, 360, 800)
        val board = composeRule.onNodeWithTag("board").getUnclippedBoundsInRoot()
        val chrome = composeRule.onNodeWithTag("play.side_panel").getUnclippedBoundsInRoot()
        val bar = composeRule.onNodeWithTag("play.action_bar").getUnclippedBoundsInRoot()

        assertTrue("chrome $chrome under board $board", chrome.top >= board.bottom)
        assertTrue("bar $bar under chrome $chrome", bar.top >= chrome.bottom)
        assertEquals("bar sits on the screen's bottom margin", 800.dp - Spacing.sm, bar.bottom)
        composeRule.onNodeWithTag("slot").assertDoesNotExist()
    }
}
