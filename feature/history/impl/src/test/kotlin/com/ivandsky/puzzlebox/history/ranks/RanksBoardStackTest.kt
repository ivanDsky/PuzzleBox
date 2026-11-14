package com.ivandsky.puzzlebox.history.ranks

import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.gameui.board.DailyBoardRowModel
import com.ivandsky.puzzlebox.gameui.board.DailyBoardState
import com.ivandsky.puzzlebox.history.HistoryLayout
import com.ivandsky.puzzlebox.model.GameId
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Where the one board stack sits in each layout, as approved on build .5: the phone insets its cards 16 dp inside a
 * swipe area that runs edge to edge; the tablet's pane is already inset, so its cards fill the pane.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class RanksBoardStackTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val places = listOf(
        BoardPlace(RanksBoard.Overall, "Overall", Standing.Place(4)),
        BoardPlace(RanksBoard.Game(GameId("zip")), "Zip", Standing.Place(2)),
    )
    private val state = RanksUiState(
        RanksBoardSlice("Zip · today", RankOrder.TimeThenHints, DailyBoardState.Ranked(listOf(DailyBoardRowModel(1, "Maya", 62_000L, 0, isSelf = false, playerId = "maya")))),
        position = 1,
        boardCount = places.size,
        places = places,
    )

    private fun show(layout: HistoryLayout) {
        composeRule.setContent { PuzzleBoxTheme { RanksScreen(state, RanksActions(onSignIn = {}, onSwipe = {}), layout = layout) } }
    }

    private fun bounds(tag: String) = composeRule.onNodeWithTag(tag).getBoundsInRoot()

    @Test
    fun `on the phone the board sits 16 dp inside the screen's sides`() {
        show(HistoryLayout.SinglePane)

        assertEquals(16.dp, bounds("ranks.board").left - bounds("route.ranks").left)
        assertEquals(16.dp, bounds("route.ranks").right - bounds("ranks.board").right)
    }

    @Test
    @Config(qualifiers = "w1280dp-h800dp-land-mdpi")
    fun `on the tablet the hero card and the board fill the right pane's width`() {
        show(HistoryLayout.TwoPane)

        val pane = bounds("ranks.rightpane")
        listOf("ranks.hero", "ranks.board").forEach { card ->
            assertEquals(card, pane.left, bounds(card).left)
            assertEquals(card, pane.right, bounds(card).right)
        }
    }
}
