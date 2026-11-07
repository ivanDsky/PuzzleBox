package com.ivandsky.puzzlebox.designsystem

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** A destructive primary pill fills with the theme's error colour; a plain one keeps primary. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class DestructiveButtonFillTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `a destructive pill fills with the error colour in light and dark, a plain one with primary`() {
        val expected = mutableMapOf<String, Color>()
        composeRule.setContent {
            Column {
                for (dark in listOf(false, true)) {
                    PuzzleBoxTheme(darkTheme = dark) {
                        for (destructive in listOf(false, true)) {
                            val tag = "dark=$dark destructive=$destructive"
                            expected[tag] = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                            PrimaryButton(text = "Go", onClick = {}, destructive = destructive, modifier = Modifier.testTag(tag))
                        }
                    }
                }
            }
        }
        composeRule.waitForIdle()
        check(expected.size == 4)
        for ((tag, color) in expected) {
            val pixels = composeRule.onNodeWithTag(tag).captureToImage().toPixelMap()
            // Inside the start cap, above the label: only the fill shows there.
            val fill = pixels[pixels.height / 2, pixels.height / 6]
            assertEquals("$tag red", color.red, fill.red, TOLERANCE)
            assertEquals("$tag green", color.green, fill.green, TOLERANCE)
            assertEquals("$tag blue", color.blue, fill.blue, TOLERANCE)
        }
    }

    private companion object {
        const val TOLERANCE = 0.02f
    }
}
