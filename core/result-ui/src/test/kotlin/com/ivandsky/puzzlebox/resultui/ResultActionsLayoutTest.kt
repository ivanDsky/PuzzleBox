package com.ivandsky.puzzlebox.resultui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.model.GameId
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The result's action pair holds one line per label at a 360 dp phone's hero width. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h800dp-420dpi")
class ResultActionsLayoutTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `share and play again sit on one row, each label on one line, at 264 dp`() {
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                Surface {
                    Box(Modifier.padding(Spacing.lg).width(264.dp)) {
                        ResultHeroActions(
                            primary = ResultPrimaryAction.Next(GameId("queens"), "Queens"),
                            onPrimary = {},
                            onShare = {},
                            onPlayAgain = {},
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()

        val shareTop = composeRule.onNodeWithTag("result.share").fetchSemanticsNode().boundsInRoot.top
        val againTop = composeRule.onNodeWithTag("result.playAgain").fetchSemanticsNode().boundsInRoot.top
        assertEquals(shareTop, againTop)
        assertEquals(1, lineCountOf("Share"))
        assertEquals(1, lineCountOf("Play again"))
    }

    private fun lineCountOf(label: String): Int {
        val node = composeRule.onNodeWithText(label, useUnmergedTree = true).fetchSemanticsNode()
        val layouts = mutableListOf<TextLayoutResult>()
        node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
        return layouts.single().lineCount
    }
}
