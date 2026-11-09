package com.ivandsky.puzzlebox.app.create

import android.content.Context
import android.content.res.Configuration
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.ivandsky.puzzlebox.create.editor.ActionArea
import com.ivandsky.puzzlebox.create.editor.StudioScreen
import com.ivandsky.puzzlebox.create.editor.ToolStrip
import com.ivandsky.puzzlebox.create.editor.VerdictCardState
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.domain.ValidatePublish
import com.ivandsky.puzzlebox.game.editor.Budget
import com.ivandsky.puzzlebox.game.editor.DraftState
import com.ivandsky.puzzlebox.game.editor.EditorDraft
import com.ivandsky.puzzlebox.game.editor.PublishBlock
import com.ivandsky.puzzlebox.game.editor.Verdict
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.games.queens.editor.QueensDraft
import com.ivandsky.puzzlebox.games.queens.editor.QueensDraftAnalyzer
import com.ivandsky.puzzlebox.games.queens.editor.QueensEditor
import com.ivandsky.puzzlebox.games.queensui.editor.QueensEditorController
import com.ivandsky.puzzlebox.games.queensui.editor.QueensEditorMode
import com.ivandsky.puzzlebox.games.queensui.editor.QueensEditorUi
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * 29: the real Queens editor inside the Studio chrome (spec S15), the one place both meet:
 * on a 320 dp and a 411 dp phone and the two-pane tablet, light and dark. Four scenes on a
 * 6×6 draft whose verdict comes from [QueensDraftAnalyzer]: Crown mode while Incomplete; 2+
 * solutions with witness A on the board and the fix list (the phone's card open in its
 * bottom sheet); No solution with a crowned clash cell; Unique with its difficulty, the readiness
 * line and the "test solve cleared" toast. Readiness is the domain's [ValidatePublish].
 * Recorded with `-Proborazzi.record=true`, verified with `-Proborazzi.verify=true`;
 * goldens in `snapshots/studio/`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w320dp-h640dp-xhdpi")
class StudioGoldenScreenshotsPhone320 : StudioGoldenScreenshots(PlayLayout.SinglePane, "phone320")

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w411dp-h891dp-420dpi")
class StudioGoldenScreenshotsPhone411 : StudioGoldenScreenshots(PlayLayout.SinglePane, "phone411")

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w1280dp-h800dp-land-mdpi")
class StudioGoldenScreenshotsTablet : StudioGoldenScreenshots(PlayLayout.TwoPane, "tablet")

abstract class StudioGoldenScreenshots(private val layout: PlayLayout, private val device: String) {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun `crown mode incomplete light`() = crownMode(dark = false)
    @Test fun `crown mode incomplete dark`() = crownMode(dark = true)

    @Test fun `multiple with witness and fix list light`() = multiple(dark = false)
    @Test fun `multiple with witness and fix list dark`() = multiple(dark = true)

    @Test fun `none with clash light`() = none(dark = false)
    @Test fun `none with clash dark`() = none(dark = true)

    @Test fun `unique with readiness and toast light`() = unique(dark = false)
    @Test fun `unique with readiness and toast dark`() = unique(dark = true)

    private fun crownMode(dark: Boolean) {
        val draft = QueensDraft(SIZE, BLANK, crowns = setOf(1, 9, 17))
        capture("crown-mode", dark, draft, analyzed<Verdict.Incomplete>(draft), mode = QueensEditorMode.Crown)
    }

    private fun multiple(dark: Boolean) {
        val draft = QueensDraft(SIZE, MULTIPLE)
        capture("multiple-witness", dark, draft, analyzed<Verdict.Multiple>(draft), openCard = true)
    }

    /** A crown on the first clash cell: the wash sits under it. */
    private fun none(dark: Boolean) {
        val verdict = analyzed<Verdict.None>(QueensDraft(SIZE, NONE))
        assertTrue("the None board names its clash", verdict.conflicts.isNotEmpty())
        capture("none-clash", dark, QueensDraft(SIZE, NONE, crowns = setOf(verdict.conflicts.min())), verdict, openCard = true)
    }

    private fun unique(dark: Boolean) {
        val draft = QueensDraft(SIZE, UNIQUE, crowns = UNIQUE_SOLUTION)
        capture("unique-toast", dark, draft, analyzed<Verdict.Unique>(draft), testCleared = true)
    }

    private inline fun <reified V : Verdict> analyzed(draft: QueensDraft): V {
        val verdict = QueensDraftAnalyzer.analyze(draft, Budget.Unlimited)
        assertTrue("expected ${V::class.simpleName}, got $verdict", verdict is V)
        return verdict as V
    }

    @OptIn(ExperimentalRoborazziApi::class)
    private fun capture(
        name: String,
        dark: Boolean,
        draft: QueensDraft,
        verdict: Verdict,
        mode: QueensEditorMode? = null,
        openCard: Boolean = false,
        testCleared: Boolean = false,
    ) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        val editorDraft = EditorDraft(QueensEditor, DraftState(draft))
        val controller = QueensEditorController(editorDraft) {}
        mode?.let(controller::setMode)
        // The Studio's own wiring: the card reads each verdict and hands its overlay to the board,
        // with Auto-show at its fresh-install default (on).
        val card = VerdictCardState(recheck = {}, controller = { controller }, autoShow = { true }).apply {
            read(verdict)
            if (openCard && layout == PlayLayout.SinglePane) show()
        }
        val readiness = ValidatePublish()(editorDraft, verdict, TITLE, testSolve = null)
        rule.mainClock.autoAdvance = false
        rule.setContent {
            val config = Configuration(LocalConfiguration.current).apply {
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                    if (dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            }
            CompositionLocalProvider(LocalConfiguration provides config) {
                // The app's root Scaffold: the background colour, onBackground content colour.
                PuzzleBoxTheme(darkTheme = dark) {
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        Studio(editorDraft, controller, verdict, card, readiness, testCleared)
                    }
                }
            }
        }
        // Past the card's scale-in, the toast's slide-in and every board mark's settle.
        rule.mainClock.advanceTimeBy(SETTLE_MS)
        captureScreenRoboImage(out(name, dark))
    }

    @Composable
    private fun Studio(
        editorDraft: EditorDraft<*>,
        controller: QueensEditorController,
        verdict: Verdict,
        card: VerdictCardState,
        readiness: List<PublishBlock>,
        testCleared: Boolean,
    ) {
        StudioScreen(
            title = TITLE,
            subtitle = "Queens ${QueensEditorUi.sizeLabel(editorDraft.variant)}",
            saving = false,
            verdict = verdict,
            cardOpen = card.open.collectAsState().value,
            overlay = controller.overlay.collectAsState().value,
            onShowCard = card::show,
            onDismissCard = card::dismiss,
            onOverlay = card::choose,
            layout = layout,
            onBack = {},
            onRename = {},
            onDuplicate = {},
            onDelete = {},
            board = { boardModifier -> QueensEditorUi.Board(controller, verdict, boardModifier) },
            toolStrip = { stripModifier ->
                ToolStrip(
                    canUndo = true,
                    canRedo = false,
                    onUndo = {},
                    onRedo = {},
                    sizes = QueensEditor.variants,
                    size = editorDraft.variant,
                    sizeLabel = QueensEditorUi::sizeLabel,
                    onResize = {},
                    onClear = {},
                    hint = { hintModifier -> QueensEditorUi.ToolHint(controller, hintModifier) },
                    modifier = stripModifier,
                )
            },
            issues = { issuesModifier ->
                QueensEditorUi.Issues(controller, verdict, editorDraft.publishBlocks(verdict), card::dismiss, issuesModifier)
            },
            actionArea = { areaModifier ->
                ActionArea(
                    passed = null,
                    canTestSolve = editorDraft.canTestSolve && verdict is Verdict.Unique,
                    onTestSolve = {},
                    blocks = readiness,
                    modifier = areaModifier,
                )
            },
            testCleared = testCleared,
        )
    }

    private fun out(name: String, dark: Boolean): String =
        File(checkNotNull(System.getProperty("user.dir")))
            .resolve("snapshots/studio/$name-$device-${if (dark) "dark" else "light"}.png")
            .canonicalFile
            .also { checkNotNull(it.parentFile).mkdirs() }
            .absolutePath

    private companion object {
        const val SIZE = 6
        const val SETTLE_MS = 2_000L
        const val TITLE = "Sunday Queens"

        val BLANK: List<Int> = List(SIZE * SIZE) { QueensDraft.UNPAINTED }

        /** One solution: [UNIQUE_SOLUTION]. */
        val UNIQUE: List<Int> = regions("BBBBAA BBBBCA EBBDCA EEDDDD EEFFDF EEFFFF")
        val UNIQUE_SOLUTION = setOf(5, 7, 16, 20, 24, 33)

        /** Two solutions, differing at 22/23 and 34/35. */
        val MULTIPLE: List<Int> = regions("AAABDD AAEBDD CAEDDD AAEEDD AAEEED AAEEFF")

        /** No solution. */
        val NONE: List<Int> = regions("CCCCAA CCCAAA CCCAAA CFFFDD FFFFDD EEBBBB")

        fun regions(rows: String): List<Int> = rows.filter { it != ' ' }.map { it - 'A' }
    }
}
