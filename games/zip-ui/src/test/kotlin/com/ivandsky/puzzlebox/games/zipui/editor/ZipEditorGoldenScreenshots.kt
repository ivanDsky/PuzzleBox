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
import com.ivandsky.puzzlebox.games.zip.Cell
import com.ivandsky.puzzlebox.games.zip.Wall
import com.ivandsky.puzzlebox.games.zip.WallDirection
import com.ivandsky.puzzlebox.games.zip.editor.ZipDraft
import com.ivandsky.puzzlebox.games.zip.editor.ZipDraftAnalyzer
import com.ivandsky.puzzlebox.games.zip.editor.ZipEditor
import com.ivandsky.puzzlebox.gameui.editor.EditorOverlay
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * The Zip editor's looks on a 411 dp phone and in the tablet Studio's left pane, light and dark:
 * each step, a partial path with its fix row, Unique, Multiple with diff rings, None with its
 * clash cells and witness A and B on the board. Every board is a real 5×5 draft whose verdict
 * comes from [ZipDraftAnalyzer], asserted before the capture.
 * Recorded with `-Proborazzi.record=true`, verified with `-Proborazzi.verify=true`; goldens in
 * `snapshots/editor/`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-420dpi")
class ZipEditorGoldenScreenshotsPhone : ZipEditorGoldenScreenshots("phone")

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w1280dp-h800dp-land-mdpi")
class ZipEditorGoldenScreenshotsTablet : ZipEditorGoldenScreenshots("tablet")

abstract class ZipEditorGoldenScreenshots(private val device: String) {

    @get:Rule
    val rule = createComposeRule()

    @Test fun `path step light`() = step("path-step", ZipEditorStep.Path, dark = false)
    @Test fun `path step dark`() = step("path-step", ZipEditorStep.Path, dark = true)

    @Test fun `numbers step light`() = step("numbers-step", ZipEditorStep.Numbers, dark = false)
    @Test fun `numbers step dark`() = step("numbers-step", ZipEditorStep.Numbers, dark = true)

    @Test fun `walls step light`() = step("walls-step", ZipEditorStep.Walls, dark = false)
    @Test fun `walls step dark`() = step("walls-step", ZipEditorStep.Walls, dark = true)

    @Test fun `partial path light`() = partial(dark = false)
    @Test fun `partial path dark`() = partial(dark = true)

    @Test fun `unique light`() = unique(dark = false)
    @Test fun `unique dark`() = unique(dark = true)

    @Test fun `multiple diff light`() = multiple("multiple-diff", dark = false)
    @Test fun `multiple diff dark`() = multiple("multiple-diff", dark = true)

    @Test fun `none clash light`() = none(dark = false)
    @Test fun `none clash dark`() = none(dark = true)

    @Test fun `witness a light`() = witness("witness-a", EditorOverlay.Witness.A, dark = false)
    @Test fun `witness a dark`() = witness("witness-a", EditorOverlay.Witness.A, dark = true)

    @Test fun `witness b light`() = witness("witness-b", EditorOverlay.Witness.B, dark = false)
    @Test fun `witness b dark`() = witness("witness-b", EditorOverlay.Witness.B, dark = true)

    private fun step(name: String, step: ZipEditorStep, dark: Boolean) {
        val draft = if (step == ZipEditorStep.Walls) WALLED else UNIQUE
        capture(name, dark, draft, analyzed<Verdict.Unique>(draft), step)
    }

    private fun partial(dark: Boolean) {
        val draft = UNIQUE.copy(
            path = SNAKE.take(PARTIAL_CELLS),
            waypoints = listOf(0, SNAKE[PARTIAL_CELLS - 1]),
        )
        capture("partial-path", dark, draft, analyzed<Verdict.Incomplete>(draft), fixList = true)
    }

    private fun unique(dark: Boolean) =
        capture("unique", dark, UNIQUE, analyzed<Verdict.Unique>(UNIQUE))

    private fun witness(name: String, which: EditorOverlay.Witness, dark: Boolean) =
        multiple(name, dark, EditorOverlay(which, showOnBoard = true))

    private fun multiple(name: String, dark: Boolean, overlay: EditorOverlay? = null) {
        val draft = ZipDraft(SIZE, emptySet(), SNAKE, listOf(0, LAST))
        capture(name, dark, draft, analyzed<Verdict.Multiple>(draft), overlay = overlay)
    }

    /** A cell walled in on all four sides: a dead end the board blames. */
    private fun none(dark: Boolean) {
        val draft = ZipDraft(SIZE, BOXED_IN, emptyList(), listOf(0, LAST))
        val verdict = analyzed<Verdict.None>(draft)
        assertTrue("the None board names its clash", verdict.conflicts.isNotEmpty())
        capture("none-clash", dark, draft, verdict)
    }

    private inline fun <reified V : Verdict> analyzed(draft: ZipDraft): V {
        val verdict = ZipDraftAnalyzer.analyze(draft, Budget.Unlimited)
        assertTrue("expected ${V::class.simpleName}, got $verdict", verdict is V)
        return verdict as V
    }

    private fun capture(
        name: String,
        dark: Boolean,
        draft: ZipDraft,
        verdict: Verdict,
        step: ZipEditorStep? = null,
        overlay: EditorOverlay? = null,
        fixList: Boolean = false,
    ) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val resolver = context.contentResolver
        Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        val controller = ZipEditorController(EditorDraft(ZipEditor, DraftState(draft))) {}
        step?.let(controller::select)
        overlay?.let(controller::adoptOverlay)
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
                            ZipEditorBoard(
                                controller,
                                Modifier.fillMaxWidth().weight(1f, fill = false),
                                verdict,
                            )
                            if (fixList) {
                                ZipEditorUi.Issues(
                                    controller,
                                    verdict,
                                    emptyList(),
                                    {},
                                    Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                }
            }
        }
        rule.mainClock.advanceTimeBy(MotionTokens.SettleMillis + SETTLE_SLACK_MS)
        rule.onNodeWithTag(GOLDEN).captureRoboImage(out(name, dark))
    }

    private fun frame(): Modifier =
        if (device == "tablet") {
            Modifier.size(TabletPaneWidth, TabletPaneHeight)
        } else {
            Modifier.width(PhoneWidth)
        }

    private fun out(name: String, dark: Boolean): String =
        File(checkNotNull(System.getProperty("user.dir")))
            .resolve("snapshots/editor/$name-$device-${if (dark) "dark" else "light"}.png")
            .canonicalFile
            .also { checkNotNull(it.parentFile).mkdirs() }
            .absolutePath

    private companion object {
        const val GOLDEN = "golden"
        const val SIZE = 5
        const val LAST = SIZE * SIZE - 1
        const val PARTIAL_CELLS = 12
        const val SETTLE_SLACK_MS = 1_000L

        /** The 411 dp phone less the capture's padding. */
        val PhoneWidth = 379.dp

        /** The tablet Studio's left pane (1280 − the 372 dp side pane) less the padding. */
        val TabletPaneWidth = 876.dp
        val TabletPaneHeight = 720.dp

        /** Row by row, alternating direction. */
        val SNAKE: List<Int> = (0 until SIZE).flatMap { row ->
            val cols = if (row % 2 == 0) 0 until SIZE else (SIZE - 1 downTo 0)
            cols.map { row * SIZE + it }
        }

        /** The snake numbered at every row end: one solution. */
        val UNIQUE: ZipDraft =
            ZipDraft(SIZE, emptySet(), SNAKE, listOf(0, 4, 9, 5, 10, 14, 19, 15, 20, 24))

        /** [UNIQUE] with walls along row 0's bottom edge between its snake neighbours. */
        val WALLED: ZipDraft = UNIQUE.copy(
            walls = setOf(
                Wall(Cell(1), WallDirection.DOWN),
                Wall(Cell(2), WallDirection.DOWN),
                Wall(Cell(3), WallDirection.DOWN),
            ),
        )

        /** Cell 6 closed on every side. */
        val BOXED_IN: Set<Wall> = setOf(
            Wall(Cell(1), WallDirection.DOWN),
            Wall(Cell(5), WallDirection.RIGHT),
            Wall(Cell(6), WallDirection.RIGHT),
            Wall(Cell(6), WallDirection.DOWN),
        )
    }
}
