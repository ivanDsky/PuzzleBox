package com.ivandsky.puzzlebox.gameui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.platform.testTag
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class BoardGridTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val taps = mutableListOf<Pair<Int, Int>>()

    private fun setContent(rows: Int, cols: Int) {
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                // Column + fillMaxWidth only: the board must derive its own height from
                // aspectRatio — no requiredSize crutch, so sizing regressions fail here.
                Column(Modifier.fillMaxWidth()) {
                    BoardGrid(
                        rows = rows,
                        cols = cols,
                        modifier = Modifier.fillMaxWidth(),
                        onCellTap = { row, col -> taps += row to col },
                    ) { row, col ->
                        Box(Modifier.fillMaxSize().testTag("cell-$row-$col"))
                        if (row == 0 && col == 0) {
                            Text("waypoint-1")
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `non-square 6x8 board lays out every cell (F3-2 pin)`() {
        setContent(rows = 6, cols = 8)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("cell-0-0").assertExists()
        composeRule.onNodeWithTag("cell-5-7").assertExists()
        composeRule.onNodeWithTag("cell-3-4").assertExists()
        composeRule.onNodeWithText("waypoint-1").assertExists()
        // explicit rows x cols: no square assumption — cell content slots are 48
        composeRule.onNodeWithTag("cell-7-0").assertDoesNotExist()
        composeRule.onNodeWithTag("cell-0-8").assertDoesNotExist()
    }

    @Test
    fun `tap reports the tapped cell`() {
        setContent(rows = 6, cols = 8)
        composeRule.onNodeWithTag("cell-1-2").performClick()
        composeRule.waitForIdle()
        assertEquals(listOf(1 to 2), taps)
    }
}
