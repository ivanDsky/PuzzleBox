package com.ivandsky.puzzlebox.designsystem

import androidx.compose.material3.MaterialTheme
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

// The muted-status chip role follows the resolved theme, reads at AA, and is its own role
// rather than a borrowed neutral surface.
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class MutedStatusTokenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun resolved(dark: Boolean): Pair<MutedStatusColors, Color> {
        var seen: MutedStatusColors? = null
        var highest = Color.Unspecified
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = dark) {
                seen = LocalMutedStatus.current
                highest = MaterialTheme.colorScheme.surfaceContainerHighest
            }
        }
        composeRule.waitForIdle()
        return checkNotNull(seen) to highest
    }

    @Test
    fun `light theme provides the light muted status`() = assertEquals(LightMutedStatus, resolved(dark = false).first)

    @Test
    fun `dark theme provides the dark muted status`() = assertEquals(DarkMutedStatus, resolved(dark = true).first)

    @Test
    fun `content clears AA on the container in both modes`() {
        listOf(LightMutedStatus, DarkMutedStatus).forEach {
            val ratio = contrast(it.content, it.container)
            assertTrue("muted status $it at $ratio:1", ratio >= 4.5f)
        }
    }

    @Test
    fun `the container is not the borrowed neutral surface`() {
        assertNotEquals(LightColorScheme.surfaceContainerHighest, LightMutedStatus.container)
        assertNotEquals(DarkColorScheme.surfaceContainerHighest, DarkMutedStatus.container)
        assertNotEquals(LightColorScheme.onSurfaceVariant, LightMutedStatus.content)
        assertNotEquals(DarkColorScheme.onSurfaceVariant, DarkMutedStatus.content)
    }

    private fun contrast(foreground: Color, background: Color): Float {
        val top = foreground.luminance() + 0.05f
        val bottom = background.luminance() + 0.05f
        return if (top > bottom) top / bottom else bottom / top
    }
}
