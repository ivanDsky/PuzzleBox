package com.ivandsky.puzzlebox.designsystem

import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

// On a wide window the dialog keeps its reading width instead of spanning the screen.
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w1280dp-h800dp")
class AppDialogWidthTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `a dialog on a wide window keeps its capped width`() {
        composeRule.setContent {
            PuzzleBoxTheme { AppDialog(title = "Delete board?", onDismissRequest = {}, modifier = Modifier.testTag("dialog")) }
        }
        composeRule.waitForIdle()

        val bounds = composeRule.onNodeWithTag("dialog").getUnclippedBoundsInRoot()
        assertEquals((380.dp + Spacing.xxl * 2).value, (bounds.right - bounds.left).value, 1f)
    }
}
