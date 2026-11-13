package com.ivandsky.puzzlebox.gameui.board

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.game.RankOrder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The viewer's own row docks to the edge of the viewport it is scrolled out through, and a tap brings it back. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class DailyBoardListDockTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val viewport = 200.dp
    private val state = ScrollState(0)
    private lateinit var scope: CoroutineScope

    private val lines: List<DailyBoardLine> = (1..20).map { rank ->
        DailyBoardLine.Real(
            DailyBoardRowModel(rank, "Player $rank", rank * 10_000L, 0, isSelf = rank == 10, playerId = "player-$rank"),
            place = rank,
        )
    }

    // The own row is the tenth: it spans 648dp to 712dp of the list (a row is 64dp, a gap 8dp).
    private val ownTop = 9 * 72

    private fun show(dock: Boolean = true) {
        composeRule.setContent {
            scope = rememberCoroutineScope()
            PuzzleBoxTheme(darkTheme = false) {
                Box(Modifier.height(viewport).verticalScroll(state)) {
                    DailyBoardList(lines, RankOrder.TimeThenHints, dock = dock)
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun scrollTo(dp: Int) {
        val px = with(composeRule.density) { dp.dp.roundToPx() }
        composeRule.runOnIdle { scope.launch { state.scrollTo(px) } }
        composeRule.waitForIdle()
    }

    private fun assertDocks(tag: String = "leaderboard.dock") = composeRule.onAllNodesWithTag(tag).assertCountEquals(1)

    @Test
    fun `a row below the viewport docks to its bottom edge`() {
        show()

        assertDocks()
        val dock = composeRule.onNodeWithTag("leaderboard.dock").getBoundsInRoot()
        assertEquals(viewport.value, dock.bottom.value, 1f)
    }

    @Test
    fun `a row above the viewport docks to its top edge`() {
        show()

        scrollTo(ownTop + 400)

        assertDocks()
        val dock = composeRule.onNodeWithTag("leaderboard.dock").getBoundsInRoot()
        assertEquals(0f, dock.top.value, 1f)
    }

    @Test
    fun `a row in view has no dock`() {
        show()

        scrollTo(ownTop - 60)

        composeRule.onAllNodesWithTag("leaderboard.dock").assertCountEquals(0)
    }

    @Test
    fun `a board that does not dock never shows one`() {
        show(dock = false)

        composeRule.onAllNodesWithTag("leaderboard.dock").assertCountEquals(0)
        scrollTo(ownTop + 400)
        composeRule.onAllNodesWithTag("leaderboard.dock").assertCountEquals(0)
    }

    @Test
    fun `the dock names the viewer's row and its place`() {
        show()

        composeRule.onNodeWithTag("leaderboard.dock").assertContentDescriptionEquals("Your row, #10. Jump to it")
    }

    @Test
    fun `a tap on the dock scrolls the row into view and the dock leaves`() {
        show()

        composeRule.onNodeWithTag("leaderboard.dock").performClick()
        composeRule.waitForIdle()

        composeRule.onAllNodesWithTag("leaderboard.dock").assertCountEquals(0)
        val own = composeRule.onNodeWithTag("daily_board.row.self").getBoundsInRoot()
        assertTrue("own row $own is inside the viewport", own.top.value >= -1f && own.bottom.value <= viewport.value + 1f)
    }

    @Test
    fun `a row that is only just out of view docks, one most of the way in does not`() {
        val visible = Rect(0f, 0f, 100f, 200f)

        assertEquals(DockEdge.Bottom, nextDockEdge(null, Rect(0f, 190f, 100f, 254f), visible))
        assertEquals(DockEdge.Top, nextDockEdge(null, Rect(0f, -50f, 100f, 14f), visible))
        assertNull(nextDockEdge(null, Rect(0f, 100f, 100f, 164f), visible))
        assertNull(nextDockEdge(null, Rect(0f, 150f, 100f, 214f), visible))
    }

    @Test
    fun `a dock that is showing stays until most of the row is back`() {
        val visible = Rect(0f, 0f, 100f, 200f)
        val half = Rect(0f, 168f, 100f, 232f)
        val mostly = Rect(0f, 150f, 100f, 214f)

        assertEquals(DockEdge.Bottom, nextDockEdge(DockEdge.Bottom, half, visible))
        assertNull(nextDockEdge(DockEdge.Bottom, mostly, visible))
        assertNull(nextDockEdge(null, half, visible))
    }

    @Test
    fun `an empty viewport or row docks nothing`() {
        assertNull(nextDockEdge(null, Rect(0f, 300f, 100f, 364f), Rect.Zero))
        assertNull(nextDockEdge(null, Rect(0f, 300f, 100f, 300f), Rect(0f, 0f, 100f, 200f)))
    }
}
