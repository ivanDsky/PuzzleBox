package com.ivandsky.puzzlebox.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.test.junit4.v2.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

// The ice accent follows the resolved theme, reads at AA, and is its own hue beside the
// existing containers.
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class IceTokenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun resolved(dark: Boolean): IceColors {
        var seen: IceColors? = null
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = dark) { seen = LocalIce.current }
        }
        composeRule.waitForIdle()
        return checkNotNull(seen)
    }

    @Test
    fun `light theme provides the light ice`() = assertEquals(LightIce, resolved(dark = false))

    @Test
    fun `dark theme provides the dark ice`() = assertEquals(DarkIce, resolved(dark = true))

    @Test
    fun `onContainer clears AA on the container in both modes`() {
        listOf(LightIce, DarkIce).forEach {
            val ratio = contrast(it.onContainer, it.container)
            assertTrue("ice $it at $ratio:1", ratio >= 4.5f)
        }
    }

    @Test
    fun `the container is not an existing container`() {
        val light = listOf(
            LightColorScheme.primaryContainer,
            LightColorScheme.tertiaryContainer,
            LightColorScheme.surfaceContainerHighest,
            LightMutedStatus.container,
        )
        val dark = listOf(
            DarkColorScheme.primaryContainer,
            DarkColorScheme.tertiaryContainer,
            DarkColorScheme.surfaceContainerHighest,
            DarkMutedStatus.container,
        )
        light.forEach { assertNotEquals(it, LightIce.container) }
        dark.forEach { assertNotEquals(it, DarkIce.container) }
    }

    private fun contrast(foreground: Color, background: Color): Float {
        val top = foreground.luminance() + 0.05f
        val bottom = background.luminance() + 0.05f
        return if (top > bottom) top / bottom else bottom / top
    }
}
