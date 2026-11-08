package com.ivandsky.puzzlebox.create.editor

import android.content.Context
import android.content.res.Configuration
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.ivandsky.puzzlebox.data.model.TestSolve
import com.ivandsky.puzzlebox.designsystem.AppShapes
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.game.VariantId
import com.ivandsky.puzzlebox.game.editor.Solution
import com.ivandsky.puzzlebox.game.editor.Verdict
import com.ivandsky.puzzlebox.gameui.PlayLayout
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * The test solve on a phone, light and dark: the sandbox while playing and once its "Solved in
 * m:ss" stamp has landed; then the Studio after the pass, with the publish details in the tools'
 * slot, and after Edit board, with the tools back, the details folded to their summary line and
 * the passed status over Publish. The board is the Studio preview's placeholder square.
 * Recorded with `-Proborazzi.record=true`, verified with `-Proborazzi.verify=true`;
 * goldens in `snapshots/test-solve/`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-420dpi")
class TestSolveGoldenScreenshots {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun `playing light`() = capture("playing", dark = false) { Sandbox(result = null) }
    @Test fun `playing dark`() = capture("playing", dark = true) { Sandbox(result = null) }

    @Test fun `solved light`() = capture("solved", dark = false) { Sandbox(PASSED) }
    @Test fun `solved dark`() = capture("solved", dark = true) { Sandbox(PASSED) }

    @Test fun `passed details light`() = capture("passed-details", dark = false) { PassedStudio(editBoard = false) }
    @Test fun `passed details dark`() = capture("passed-details", dark = true) { PassedStudio(editBoard = false) }

    @Test fun `passed edit board light`() = capture("passed-edit-board", dark = false) { PassedStudio(editBoard = true) }
    @Test fun `passed edit board dark`() = capture("passed-edit-board", dark = true) { PassedStudio(editBoard = true) }

    @OptIn(ExperimentalRoborazziApi::class)
    private fun capture(name: String, dark: Boolean, content: @Composable () -> Unit) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        rule.mainClock.autoAdvance = false
        rule.setContent {
            val config = Configuration(LocalConfiguration.current).apply {
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                    if (dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            }
            CompositionLocalProvider(LocalConfiguration provides config) {
                // The app's root Scaffold: the background colour, onBackground content colour.
                PuzzleBoxTheme(darkTheme = dark) {
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { content() }
                }
            }
        }
        rule.mainClock.advanceTimeBy(SETTLE_MS)
        captureScreenRoboImage(out(name, dark))
    }

    @Composable
    private fun Sandbox(result: TestSolve?) {
        TestSolveScreen(result = result, onBack = {}, board = { PlaceholderBoard(it) })
    }

    @Composable
    private fun PassedStudio(editBoard: Boolean) {
        StudioScreen(
            title = TITLE,
            subtitle = "Queens 6×6",
            saving = false,
            verdict = UNIQUE,
            layout = PlayLayout.SinglePane,
            onBack = {},
            onRename = {},
            onDuplicate = {},
            onDelete = {},
            board = { PlaceholderBoard(it) },
            toolStrip = { slotModifier ->
                ToolSlot(
                    open = true,
                    editBoard = editBoard,
                    onEditBoard = {},
                    tools = { Tools(it) },
                    form = { formModifier, editBoardChip ->
                        PublishDetails(
                            title = TITLE,
                            details = DETAILS,
                            signedIn = true,
                            onRename = {},
                            onAuthorName = {},
                            onNote = {},
                            modifier = formModifier,
                            paired = true,
                            action = editBoardChip,
                        )
                    },
                    modifier = slotModifier,
                )
            },
            actionArea = { areaModifier ->
                ActionArea(
                    passed = PASSED,
                    canTestSolve = true,
                    onTestSolve = {},
                    blocks = emptyList(),
                    modifier = areaModifier,
                    publish = PublishSession.State(details = DETAILS),
                    title = TITLE,
                    onShowDetails = {}.takeIf { editBoard },
                )
            },
        )
    }

    @Composable
    private fun Tools(modifier: Modifier) {
        ToolStrip(
            canUndo = true,
            canRedo = false,
            onUndo = {},
            onRedo = {},
            sizes = listOf(SIZE),
            size = SIZE,
            sizeLabel = { "6×6" },
            onResize = {},
            onClear = {},
            hint = { Text("Drag across cells, then pick a colour", it, style = MaterialTheme.typography.bodySmall) },
            modifier = modifier,
        )
    }

    @Composable
    private fun PlaceholderBoard(modifier: Modifier) {
        Box(modifier, contentAlignment = Alignment.Center) {
            Box(Modifier.aspectRatio(1f, matchHeightConstraintsFirst = true).background(MaterialTheme.colorScheme.surfaceContainerHighest, AppShapes.small))
        }
    }

    private fun out(name: String, dark: Boolean): String =
        File(checkNotNull(System.getProperty("user.dir")))
            .resolve("snapshots/test-solve/$name-phone-${if (dark) "dark" else "light"}.png")
            .canonicalFile
            .also { checkNotNull(it.parentFile).mkdirs() }
            .absolutePath

    private companion object {
        const val SETTLE_MS = 2_000L
        const val TITLE = "Sunday Queens"

        val SIZE = VariantId("queens/6")
        val UNIQUE = Verdict.Unique(Solution(listOf(1, 9, 12, 22, 26, 35)))
        val PASSED = TestSolve(layoutKey = "golden", timeMs = 102_000, hints = 0)
        val DETAILS = PublishSession.Details(authorName = "Ivan", note = "Start from the corner.")
    }
}
