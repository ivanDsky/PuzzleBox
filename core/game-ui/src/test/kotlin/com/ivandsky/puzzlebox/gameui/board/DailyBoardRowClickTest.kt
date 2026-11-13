package com.ivandsky.puzzlebox.gameui.board

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.game.RankOrder
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** A board's row taps are opt-in, and only another player's official row takes one. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class DailyBoardRowClickTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun row(rank: Int, name: String, self: Boolean = false, unofficial: Boolean = false) =
        DailyBoardRowModel(rank, name, 60_000L * rank, 0, isSelf = self, playerId = name.lowercase(), unofficial = unofficial)

    private fun show(onRowClick: ((DailyBoardRowModel) -> Unit)?) = composeRule.setContent {
        PuzzleBoxTheme {
            DailyBoard(
                "Zip",
                RankOrder.TimeThenHints,
                DailyBoardState.Ranked(listOf(row(1, "Maya"), row(2, "Me", self = true), row(3, "Cy", unofficial = true))),
                onRowClick = onRowClick,
            )
        }
    }

    @Test
    fun `another player's row reports itself`() {
        val tapped = mutableListOf<String>()
        show { tapped += it.playerId }

        composeRule.onNodeWithText("Maya").performClick()

        assertEquals(listOf("maya"), tapped)
    }

    @Test
    fun `the viewer's row and an unofficial row take no tap`() {
        val tapped = mutableListOf<String>()
        show { tapped += it.playerId }

        composeRule.onNodeWithText("Me").performClick()
        composeRule.onNodeWithText("Cy").performClick()

        assertEquals(emptyList<String>(), tapped)
    }

    @Test
    fun `a board that did not opt in has rows that take no tap`() {
        show(null)

        composeRule.onNodeWithText("Maya").performClick()
    }
}
