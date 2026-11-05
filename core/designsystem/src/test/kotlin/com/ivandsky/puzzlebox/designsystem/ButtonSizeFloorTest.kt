package com.ivandsky.puzzlebox.designsystem

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

// Spec A8 size floor: every shared button is at least 48 dp tall in light and dark,
// enabled and disabled. Material's touch-target padding is switched off so the test
// measures the button's own drawn height, not the invisible 48 dp hit area Material
// wraps around its 40 dp default button.
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class ButtonSizeFloorTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `every shared button is at least 48 dp tall in light and dark`() {
        composeRule.setContent {
            // Light and dark side by side: one stacked column of all twelve would run past
            // the 470 dp Robolectric screen and clip the last buttons' bounds.
            CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
                Row {
                    for (dark in listOf(false, true)) {
                        PuzzleBoxTheme(darkTheme = dark) {
                            Column {
                                for (enabled in listOf(true, false)) {
                                    PrimaryButton("Primary", onClick = {}, enabled = enabled)
                                    SecondaryButton("Secondary", onClick = {}, enabled = enabled)
                                    AppTextButton("Text", onClick = {}, enabled = enabled)
                                }
                            }
                        }
                    }
                }
            }
        }
        for (label in listOf("Primary", "Secondary", "Text")) {
            val nodes = composeRule.onAllNodes(hasText(label))
            val count = nodes.fetchSemanticsNodes().size
            assertEquals("'$label' buttons (light/dark x enabled/disabled)", 4, count)
            for (i in 0 until count) nodes[i].assertHeightIsAtLeast(48.dp)
        }
    }
}
