package com.ivandsky.puzzlebox.games.queensui.trainer

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
import java.io.File
import kotlinx.coroutines.Dispatchers
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The snippet board on a compact phone, for a queen step and for a cross step: the cut's queens and crosses, with the
 * crosses its queens force. Rendered with `-Proborazzi.record=true` into `snapshots/`; review evidence, not committed
 * (ADR-0033).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-420dpi")
class QueensTrainerSnippetGoldenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `snippet board for a queen step`() = capture("place", QueensTrainerFixtures.snippets.filter { it.placesQueen })

    @Test
    fun `snippet board for a cross step`() = capture("cross", QueensTrainerFixtures.snippets.filter { !it.placesQueen })

    private fun capture(name: String, candidates: List<QueensSnippet>) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        val snippet = candidates.maxBy { it.cut.boardBefore.queens.size }
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                Box(Modifier.padding(16.dp)) {
                    QueensSnippetBoard(snippet, Dispatchers.Default, {}, Modifier.size(328.dp))
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage(
            File(checkNotNull(System.getProperty("user.dir")))
                .resolve("snapshots/queens-trainer-snippet-$name.png")
                .canonicalFile
                .absolutePath,
        )
    }
}
