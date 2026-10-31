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

// The tall screen (h3400dp) fits the whole catalog in one capture; both images are
// written straight to the ticket evidence directory.
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w440dp-h3400dp-420dpi")
class CatalogScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `catalog renders the full token set in light`() {
        capture(darkTheme = false, name = "catalog-light.png")
    }

    @Test
    fun `catalog renders the full token set in dark`() {
        capture(darkTheme = true, name = "catalog-dark.png")
    }

    private fun capture(darkTheme: Boolean, name: String) {
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = darkTheme) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background),
                ) {
                    TokenCatalogScreen(Modifier.padding(top = Spacing.xl))
                }
            }
        }
        composeRule.onRoot()
            .captureRoboImage(File(SNAPSHOT_DIR, name).absolutePath)
    }

    companion object {
        private val SNAPSHOT_DIR = checkNotNull(System.getProperty("user.dir"))
            .let(::File)
            .resolve("../..")
            .canonicalFile
            .resolve(".scratch/puzzlebox/reports/02-evidence/snapshots")
    }
}
