package com.ivandsky.puzzlebox.games.queensui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.assertCountEquals
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.games.queens.render.QueensRendererModel
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class QueensBoardRendererTest {

    @get:Rule
    val composeRule = createComposeRule()

    /** 4x4 sample: checkerboard regions, queens on cells 0 and 15. */
    private fun sampleModel(queens: Set<Int> = setOf(0, 15)) = QueensRendererModel(
        n = 4,
        regions = List(16) { (it / 4 + it % 4) % 4 },
        queens = queens,
        hintHighlight = null,
        status = QueensRendererModel.Status.IDLE,
    )

    private fun setContent(model: QueensRendererModel) {
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                QueensBoardRenderer().Content(model, Modifier.fillMaxWidth())
            }
        }
    }

    @Test
    fun `renders placed queens without throwing`() {
        setContent(sampleModel())
        composeRule.waitForIdle()
        composeRule.onAllNodesWithTag(QUEEN_TAG, useUnmergedTree = true).assertCountEquals(2)
    }

    @Test
    fun `empty board composes without throwing`() {
        setContent(sampleModel(queens = emptySet()))
        composeRule.waitForIdle()
    }
}
