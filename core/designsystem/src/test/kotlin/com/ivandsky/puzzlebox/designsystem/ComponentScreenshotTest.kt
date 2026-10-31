package com.ivandsky.puzzlebox.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

// The components half of the catalog (buttons, chips, board, dialog, top bars,
// states) captured in both themes. Goldens are committed under core/designsystem/snapshots/.
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w440dp-h3400dp-420dpi")
class ComponentScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `components render in light`() {
        capture(darkTheme = false, name = "components-light.png")
    }

    @Test
    fun `components render in dark`() {
        capture(darkTheme = true, name = "components-dark.png")
    }

    private fun capture(darkTheme: Boolean, name: String) {
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = darkTheme) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background),
                ) {
                    ComponentCatalogScreen(Modifier.padding(top = Spacing.xl))
                }
            }
        }
        composeRule.onRoot()
            .captureRoboImage(File(SNAPSHOT_DIR, name).absolutePath)
    }

    companion object {
        private val SNAPSHOT_DIR = File(System.getProperty("user.dir")).resolve("snapshots")
    }
}
