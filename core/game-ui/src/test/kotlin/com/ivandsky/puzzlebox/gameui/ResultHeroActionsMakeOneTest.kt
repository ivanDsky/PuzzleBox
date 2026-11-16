package com.ivandsky.puzzlebox.gameui

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.height
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The result's "Make one like this": a plain button in the actions, there only when a handler is given. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class ResultHeroActionsMakeOneTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var made = 0
    private var next = 0

    private fun show(onMakeOneLikeThis: (() -> Unit)?) {
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                ResultHeroActions(
                    primary = ResultPrimaryAction.Done,
                    onPrimary = { next++ },
                    onShare = {},
                    onPlayAgain = {},
                    onMakeOneLikeThis = onMakeOneLikeThis,
                )
            }
        }
    }

    @Test
    fun `with a handler the button shows by its words and calls it once per tap`() {
        show { made++ }
        composeRule.onNodeWithTag("result.makeOne").assertTextEquals("Make one like this").assertHasClickAction()
        composeRule.onNodeWithTag("result.makeOne").performClick()
        composeRule.runOnIdle {
            assertEquals(1, made)
            assertEquals("the primary action is untouched", 0, next)
        }
    }

    @Test
    fun `the button is a full touch target`() {
        show { made++ }
        val bounds = composeRule.onNodeWithTag("result.makeOne").getUnclippedBoundsInRoot()
        assertTrue("height ${bounds.height}", bounds.height.value >= 48f)
    }

    @Test
    fun `with no handler the row has no such button`() {
        show(onMakeOneLikeThis = null)
        composeRule.onNodeWithTag("result.done").assertHasClickAction()
        composeRule.onNodeWithTag("result.makeOne").assertDoesNotExist()
    }
}
