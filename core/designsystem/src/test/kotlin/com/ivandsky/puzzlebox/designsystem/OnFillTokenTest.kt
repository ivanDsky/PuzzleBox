package com.ivandsky.puzzlebox.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.v2.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

// The on-fill role follows the resolved theme, and the status marks render in the icon sheet.
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class OnFillTokenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun onFill(dark: Boolean): Color {
        var seen = Color.Unspecified
        composeRule.setContent { PuzzleBoxTheme(darkTheme = dark) { seen = LocalOnFill.current } }
        composeRule.waitForIdle()
        return seen
    }

    @Test
    fun `light on-fill is white`() = assertEquals(Color(0xFFFFFFFF), onFill(dark = false))

    @Test
    fun `dark on-fill is the dark surface`() = assertEquals(Color(0xFF101418), onFill(dark = true))

    @Test
    fun `icon sheet renders with the status marks`() {
        composeRule.setContent { PuzzleIconsPreview() }
        composeRule.waitForIdle()
        listOf(PuzzleIcons.StatusRing, PuzzleIcons.StatusHalf, PuzzleIcons.Retry).forEach {
            assertEquals(24f, it.viewportWidth)
        }
    }
}
