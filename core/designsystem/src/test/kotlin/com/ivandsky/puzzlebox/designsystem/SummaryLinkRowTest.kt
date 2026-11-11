package com.ivandsky.puzzlebox.designsystem

import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

// F6: the row is a 56dp pill for one line of text; a longer text grows the row instead of crowding the pill.
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h640dp")
class SummaryLinkRowTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun show(text: String) {
        composeRule.setContent {
            PuzzleBoxTheme { SummaryLinkRow(text, onClick = {}, modifier = Modifier.testTag("row")) }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun `one line keeps the 56dp pill`() {
        show("Your history")

        assertEquals(56f, composeRule.onNodeWithTag("row").getUnclippedBoundsInRoot().height.value, 0.5f)
    }

    @Test
    fun `a long text wraps with room above and below inside a taller row`() {
        val text = "Today's rank: #12 on Mini Sudoku, the hardest board of the whole week"
        show(text)

        val row = composeRule.onNodeWithTag("row").getUnclippedBoundsInRoot()
        val label = composeRule.onNodeWithText(text, useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertTrue("row $row did not grow past 56dp", row.height > 56.dp)
        assertTrue("text $label touches the edges of row $row", row.height - label.height >= Spacing.sm * 2 - 0.5.dp)
    }
}
