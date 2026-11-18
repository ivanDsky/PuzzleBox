package com.ivandsky.puzzlebox.resultui.board

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.game.RankOrder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** A [SelfRowJump] asked from outside the board does what a tap on the dock does: the viewer's row scrolls into view. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class SelfRowJumpTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val viewport = 200.dp
    private val jump = SelfRowJump()
    private lateinit var scope: CoroutineScope

    private fun line(rank: Int, self: Boolean) =
        DailyBoardLine.Real(DailyBoardRowModel(rank, "Player $rank", rank * 10_000L, 0, isSelf = self, playerId = "player-$rank"), place = rank)

    private fun show(lines: List<DailyBoardLine>, dock: Boolean = true, provided: SelfRowJump? = jump) {
        composeRule.setContent {
            scope = rememberCoroutineScope()
            PuzzleBoxTheme(darkTheme = false) {
                CompositionLocalProvider(LocalSelfRowJump provides provided) {
                    Box(Modifier.height(viewport).verticalScroll(ScrollState(0))) {
                        DailyBoardList(lines, RankOrder.TimeThenHints, style = DailyBoardStyle.defaults(dock = dock))
                    }
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun ask(): Boolean {
        var asked = false
        composeRule.runOnIdle { scope.launch { asked = jump.request() } }
        composeRule.waitForIdle()
        return asked
    }

    @Test
    fun `a request scrolls the viewer's row into view and the dock leaves`() {
        show((1..20).map { line(it, self = it == 10) })
        composeRule.onAllNodesWithTag("leaderboard.dock").assertCountEquals(1)

        assertTrue(ask())

        composeRule.onAllNodesWithTag("leaderboard.dock").assertCountEquals(0)
        val own = composeRule.onNodeWithTag("daily_board.row.self").getBoundsInRoot()
        assertTrue("own row $own is inside the viewport", own.top.value >= -1f && own.bottom.value <= viewport.value + 1f)
    }

    @Test
    fun `a board with no row of the viewer's has nothing to jump to`() {
        show((1..20).map { line(it, self = false) })

        assertFalse(ask())
    }

    @Test
    fun `a board that does not dock has nothing to jump to`() {
        show((1..20).map { line(it, self = it == 10) }, dock = false)

        assertFalse(ask())
    }
}
