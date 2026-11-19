package com.ivandsky.puzzlebox.games.zipui.editor

import android.content.Context
import android.content.res.Configuration
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.game.editor.Budget
import com.ivandsky.puzzlebox.game.editor.DraftState
import com.ivandsky.puzzlebox.game.editor.EditorDraft
import com.ivandsky.puzzlebox.game.editor.Verdict
import com.ivandsky.puzzlebox.games.zip.editor.ZipDraft
import com.ivandsky.puzzlebox.games.zip.editor.ZipDraftAnalyzer
import com.ivandsky.puzzlebox.games.zip.editor.ZipEditor
import com.ivandsky.puzzlebox.gameui.editor.EditorOverlay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * A 7×7 board with two solutions and 15 differing cells (ticket 1544's "Mighty Candle" shape),
 * drawn with Show on board on: Solution A and Solution B, light and dark, on a 411 dp phone and
 * in the tablet Studio's left pane. Rendered with `-Proborazzi.record=true` into
 * `snapshots/editor/` (untracked review captures).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-420dpi")
class ZipEditorTwoSolutionsScreenshotsPhone : ZipEditorTwoSolutionsScreenshots("phone")

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w1280dp-h800dp-land-mdpi")
class ZipEditorTwoSolutionsScreenshotsTablet : ZipEditorTwoSolutionsScreenshots("tablet")

abstract class ZipEditorTwoSolutionsScreenshots(private val device: String) {

    @get:Rule
    val rule = createComposeRule()

    @Test fun `solution a light`() = capture("two-solutions-a", EditorOverlay.Witness.A, dark = false)
    @Test fun `solution a dark`() = capture("two-solutions-a", EditorOverlay.Witness.A, dark = true)

    @Test fun `solution b light`() = capture("two-solutions-b", EditorOverlay.Witness.B, dark = false)
    @Test fun `solution b dark`() = capture("two-solutions-b", EditorOverlay.Witness.B, dark = true)

    private fun capture(name: String, witness: EditorOverlay.Witness, dark: Boolean) {
        val verdict = ZipDraftAnalyzer.analyze(DRAFT, Budget.Unlimited)
        assertTrue("expected Multiple, got $verdict", verdict is Verdict.Multiple)
        assertEquals(DIFFERING_CELLS, (verdict as Verdict.Multiple).differingCells.size)
        val context = ApplicationProvider.getApplicationContext<Context>()
        Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        val controller = ZipEditorController(EditorDraft(ZipEditor, DraftState(DRAFT))) {}
        controller.adoptOverlay(EditorOverlay(witness, showOnBoard = true))
        rule.mainClock.autoAdvance = false
        rule.setContent {
            val config = Configuration(LocalConfiguration.current).apply {
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                    if (dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            }
            CompositionLocalProvider(LocalConfiguration provides config) {
                PuzzleBoxTheme(darkTheme = dark) {
                    Surface(Modifier.testTag(GOLDEN)) {
                        Column(Modifier.padding(Spacing.md).then(frame())) {
                            ZipEditorBoard(controller, Modifier.fillMaxWidth().weight(1f, fill = false), verdict)
                        }
                    }
                }
            }
        }
        rule.mainClock.advanceTimeBy(MotionTokens.SettleMillis + SETTLE_SLACK_MS)
        rule.onNodeWithTag(GOLDEN).captureRoboImage(out(name, dark))
    }

    private fun frame(): Modifier =
        if (device == "tablet") Modifier.size(TabletPaneWidth, TabletPaneHeight) else Modifier.width(PhoneWidth)

    private fun out(name: String, dark: Boolean): String =
        File(checkNotNull(System.getProperty("user.dir")))
            .resolve("snapshots/editor/$name-$device-${if (dark) "dark" else "light"}.png")
            .canonicalFile
            .also { checkNotNull(it.parentFile).mkdirs() }
            .absolutePath

    private companion object {
        const val GOLDEN = "golden"
        const val DIFFERING_CELLS = 15
        const val SETTLE_SLACK_MS = 1_000L

        /** The 411 dp phone less the capture's padding. */
        val PhoneWidth = 379.dp

        /** The tablet Studio's left pane (1280 − the 372 dp side pane) less the padding. */
        val TabletPaneWidth = 876.dp
        val TabletPaneHeight = 720.dp

        /** Drawn with only its two ends numbered, so the analyzer finds a second path of its own. */
        val DRAFT: ZipDraft = ZipDraft(
            n = 7,
            walls = emptySet(),
            path = listOf(
                0, 1, 2, 3, 10, 9, 8, 7, 14, 15, 16, 17, 24, 25, 26, 33, 40, 39, 32, 31, 38, 37, 36, 29, 30,
                23, 22, 21, 28, 35, 42, 43, 44, 45, 46, 47, 48, 41, 34, 27, 20, 19, 12, 13, 6, 5, 4, 11, 18,
            ),
            waypoints = listOf(0, 18),
        )
    }
}
