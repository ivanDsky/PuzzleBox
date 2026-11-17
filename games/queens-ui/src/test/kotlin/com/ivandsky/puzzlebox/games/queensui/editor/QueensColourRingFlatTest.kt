package com.ivandsky.puzzlebox.games.queensui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Flat surfaces (nav-rethink M10-8): the colour ring's disc has no drop shadow, a 1 dp outline holds its edge
 * instead. Flat off keeps the shadow and no outline. Goldens: `snapshots/editor/ring-{flat,raised}-*.png`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w400dp-h800dp-xxhdpi")
class QueensColourRingFlatTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var outline = Color.Unspecified

    private fun show(flat: Boolean, dark: Boolean = false) {
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = dark, flatSurfaces = flat) {
                outline = MaterialTheme.colorScheme.outlineVariant
                Scene()
            }
        }
        composeRule.waitForIdle()
    }

    @Composable
    private fun Scene() {
        Box(Modifier.testTag(SCENE).background(MaterialTheme.colorScheme.background).padding(16.dp)) {
            QueensColourRing(colours = 9, used = setOf(0, 1, 2), current = 1, onPick = {})
        }
    }

    /** The ring's left-most pixel column at its vertical middle: where the outline sits when there is one. */
    private fun edgePixel(): Color {
        val image = composeRule.onNodeWithTag("queens.editor.ring").captureToImage().toPixelMap()
        return image[1, image.height / 2]
    }

    @Test
    fun `flat on, the disc edge is the outline`() {
        show(flat = true)
        assertEquals(outline, edgePixel())
    }

    @Test
    fun `flat off, the disc has no outline`() {
        show(flat = false)
        assertNotEquals(outline, edgePixel())
    }

    @Test fun `golden flat light`() = golden(flat = true, dark = false)

    @Test fun `golden flat dark`() = golden(flat = true, dark = true)

    @Test fun `golden raised light`() = golden(flat = false, dark = false)

    @Test fun `golden raised dark`() = golden(flat = false, dark = true)

    private fun golden(flat: Boolean, dark: Boolean) {
        show(flat, dark)
        val out = File(checkNotNull(System.getProperty("user.dir")))
            .resolve("snapshots/editor/ring-${if (flat) "flat" else "raised"}-${if (dark) "dark" else "light"}.png")
            .canonicalFile
            .also { checkNotNull(it.parentFile).mkdirs() }
        composeRule.onNodeWithTag(SCENE).captureRoboImage(out.absolutePath)
    }

    private companion object {
        const val SCENE = "scene"
    }
}
