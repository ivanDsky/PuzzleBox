package com.ivandsky.puzzlebox.games.tangoui.trainer

import android.content.Context
import android.provider.Settings
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.games.tango.TangoCell
import java.io.File
import kotlinx.coroutines.Dispatchers
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The snippet board on a compact phone, for a step that places a sun and for one that places a moon: the cut's givens
 * and the cells the walk had filled, the signs, and the target cell still open. Rendered with `-Proborazzi.record=true`
 * into `snapshots/`; review evidence, not committed (ADR-0033).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-420dpi")
class TangoTrainerSnippetGoldenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `snippet board for a sun step`() = capture("sun", TangoCell.SUN)

    @Test
    fun `snippet board for a moon step`() = capture("moon", TangoCell.MOON)

    private fun capture(name: String, value: TangoCell) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        val snippet = TangoTrainerFixtures.snippets.filter { it.value == value }.maxBy { snippet -> snippet.cut.gridBefore.count { it != null } }
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                Box(Modifier.padding(16.dp)) {
                    TangoSnippetBoard(snippet, Dispatchers.Default, {}, Modifier.size(328.dp))
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage(
            File(checkNotNull(System.getProperty("user.dir")))
                .resolve("snapshots/tango-trainer-snippet-$name.png")
                .canonicalFile
                .absolutePath,
        )
    }
}
