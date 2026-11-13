package com.ivandsky.puzzlebox.app.create

import android.content.Context
import android.content.res.Configuration
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.ivandsky.puzzlebox.app.DrainTestApplication
import com.ivandsky.puzzlebox.create.editor.ActionArea
import com.ivandsky.puzzlebox.create.editor.PublishDetails
import com.ivandsky.puzzlebox.create.editor.PublishSession
import com.ivandsky.puzzlebox.create.editor.StudioScreen
import com.ivandsky.puzzlebox.create.editor.ToolSlot
import com.ivandsky.puzzlebox.create.editor.ToolStrip
import com.ivandsky.puzzlebox.create.editor.UndoRedoButtons
import com.ivandsky.puzzlebox.data.model.TestSolve
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.domain.ValidatePublish
import com.ivandsky.puzzlebox.game.editor.Budget
import com.ivandsky.puzzlebox.game.editor.DraftState
import com.ivandsky.puzzlebox.game.editor.EditorDraft
import com.ivandsky.puzzlebox.game.editor.Verdict
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.games.strands.editor.StrandsDraft
import com.ivandsky.puzzlebox.games.strands.editor.StrandsDraftAnalyzer
import com.ivandsky.puzzlebox.games.strands.editor.StrandsDraftPath
import com.ivandsky.puzzlebox.games.strands.editor.StrandsEditor
import com.ivandsky.puzzlebox.games.strandsui.editor.StrandsEditorController
import com.ivandsky.puzzlebox.games.strandsui.editor.StrandsEditorUi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * The real Strands editor inside the Studio chrome, on a 411 dp phone and the two-pane tablet,
 * light and dark, over a Unique eight-word board whose WINDOW spangram is selected. Authoring:
 * on the tablet the board fills the left pane alone and the word field, the spangram toggle and
 * the eight path rows follow the tool strip in the side pane, nothing scrolled (asserted); on the
 * phone they sit under the board. Publish: after a passed test the Route's [ToolSlot] swaps the
 * tools for the details, headed with the Edit board chip, which close with the theme clue and
 * the word list credit. Readiness is the domain's
 * [ValidatePublish]. Recorded with `-Proborazzi.record=true`, verified with
 * `-Proborazzi.verify=true`; goldens in `snapshots/studio/`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = DrainTestApplication::class, qualifiers = "w411dp-h891dp-420dpi")
class StrandsStudioGoldenScreenshotsPhone411 : StrandsStudioGoldenScreenshots(PlayLayout.SinglePane, "phone411")

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = DrainTestApplication::class, qualifiers = "w1280dp-h800dp-land-mdpi")
class StrandsStudioGoldenScreenshotsTablet : StrandsStudioGoldenScreenshots(PlayLayout.TwoPane, "tablet")

abstract class StrandsStudioGoldenScreenshots(private val layout: PlayLayout, private val device: String) {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun `authoring light`() = capture("strands-authoring", dark = false, publishing = false)
    @Test fun `authoring dark`() = capture("strands-authoring", dark = true, publishing = false)

    @Test fun `publish details with clue and credit light`() = capture("strands-publish", dark = false, publishing = true)
    @Test fun `publish details with clue and credit dark`() = capture("strands-publish", dark = true, publishing = true)

    /** The DoD: the board and every authoring control on one tablet screen, no scroll anywhere. */
    @Test
    fun `the tablet shows the board and every authoring control without scrolling`() {
        assumeTrue(layout == PlayLayout.TwoPane)
        show(dark = false, publishing = false)
        rule.onNodeWithTag("strands.editor.spangram.$SPANGRAM").assertExists()
        rule.onNodeWithTag("strands.editor.word").assertExists()
        val pane = rule.onNodeWithTag(SIDE_PANE).fetchSemanticsNode().boundsInRoot
        val lastPath = rule.onNodeWithTag("strands.editor.path.${WORDS.lastIndex}").fetchSemanticsNode().boundsInRoot
        val testSolve = rule.onNodeWithTag("create.action.testSolve").fetchSemanticsNode().boundsInRoot
        assertTrue("last path $lastPath above Test solve $testSolve", lastPath.bottom <= testSolve.top)
        assertTrue("last path $lastPath inside the side pane $pane", lastPath.bottom <= pane.bottom)
        val scrollers = rule.onAllNodes(hasScrollAction()).fetchSemanticsNodes()
        scrollers.forEach { node ->
            val range = node.config.getOrNull(SemanticsProperties.VerticalScrollAxisRange) ?: return@forEach
            assertEquals("nothing left to scroll in ${node.config}", 0f, range.maxValue(), 0f)
        }
    }

    private fun draft(): StrandsDraft {
        val letters = WORDS.joinToString("")
        val paths = WORDS.mapIndexed { row, word ->
            StrandsDraftPath(List(StrandsDraft.COLS) { row * StrandsDraft.COLS + it }, word)
        }
        return StrandsDraft(letters, CLUE, paths, SPANGRAM)
    }

    @OptIn(ExperimentalRoborazziApi::class)
    private fun capture(name: String, dark: Boolean, publishing: Boolean) {
        show(dark, publishing)
        captureScreenRoboImage(out(name, dark))
    }

    private fun show(dark: Boolean, publishing: Boolean) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        val draft = draft()
        val verdict = StrandsDraftAnalyzer.analyze(draft, Budget.Unlimited)
        assertTrue("expected Unique, got $verdict", verdict is Verdict.Unique)
        val editorDraft = EditorDraft(StrandsEditor, DraftState(draft))
        val controller = StrandsEditorController(editorDraft) {}
        if (!publishing) controller.selectPath(SPANGRAM)
        val passed = TestSolve(editorDraft.layoutKey, timeMs = 95_000, hints = 0).takeIf { publishing }
        rule.mainClock.autoAdvance = false
        rule.setContent {
            val config = Configuration(LocalConfiguration.current).apply {
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                    if (dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            }
            CompositionLocalProvider(LocalConfiguration provides config) {
                PuzzleBoxTheme(darkTheme = dark) {
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        Studio(editorDraft, controller, verdict, passed)
                    }
                }
            }
        }
        rule.mainClock.advanceTimeBy(SETTLE_MS)
    }

    @Composable
    private fun Studio(
        editorDraft: EditorDraft<*>,
        controller: StrandsEditorController,
        verdict: Verdict,
        passed: TestSolve?,
    ) {
        val readiness = ValidatePublish()(editorDraft, verdict, TITLE, passed)
        StudioScreen(
            title = TITLE,
            subtitle = "Strands ${StrandsEditorUi.sizeLabel(editorDraft.variant)}",
            saving = false,
            verdict = verdict,
            cardOpen = false,
            overlay = controller.overlay.collectAsState().value,
            onShowCard = {},
            onDismissCard = {},
            onOverlay = {},
            layout = layout,
            onBack = {},
            onRename = {},
            onRollTitle = { "Amber Falcon" },
            onDuplicate = {},
            onDelete = {},
            board = { boardModifier -> StrandsEditorUi.Board(controller, verdict, layout, boardModifier) },
            // The Route's own host: the tools, or once the test passes the details in their place,
            // headed "Publish details" with the Edit board chip, the slot holding the taller of the two while they show.
            toolStrip = { slotModifier ->
                ToolSlot(
                    open = passed != null,
                    editBoard = false,
                    onEditBoard = {},
                    tools = { stripModifier ->
                        Column(stripModifier) {
                            ToolStrip(
                                canUndo = true,
                                canRedo = false,
                                onUndo = {},
                                onRedo = {},
                                sizes = StrandsEditor.variants,
                                size = editorDraft.variant,
                                sizeLabel = StrandsEditorUi::sizeLabel,
                                onResize = {},
                                onClear = {},
                                hint = { hintModifier -> StrandsEditorUi.ToolHint(controller, hintModifier) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            if (layout == PlayLayout.TwoPane) StrandsEditorUi.PaneTools(controller, Modifier.fillMaxWidth())
                        }
                    },
                    form = { formModifier, editBoardChip ->
                        PublishDetails(
                            title = TITLE,
                            details = PublishSession.Details(authorName = "Ivan"),
                            signedIn = true,
                            onRename = {},
                            onRollTitle = { "Amber Falcon" },
                            onAuthorName = {},
                            onNote = {},
                            modifier = formModifier,
                            paired = true,
                            action = editBoardChip,
                            history = { UndoRedoButtons(canUndo = true, canRedo = false, onUndo = {}, onRedo = {}) },
                            fields = { fieldsModifier -> StrandsEditorUi.PublishFields(controller, true, fieldsModifier) },
                        )
                    },
                    modifier = slotModifier,
                )
            },
            issues = { issuesModifier ->
                StrandsEditorUi.Issues(controller, verdict, editorDraft.publishBlocks(verdict), {}, issuesModifier)
            },
            actionArea = { areaModifier ->
                ActionArea(
                    passed = passed,
                    canTestSolve = editorDraft.canTestSolve,
                    onTestSolve = {},
                    blocks = readiness,
                    modifier = areaModifier,
                    publish = PublishSession.State().takeIf { passed != null },
                    title = TITLE,
                    fieldCopy = StrandsEditorUi.publishFieldCopy,
                )
            },
        )
    }

    private fun out(name: String, dark: Boolean): String =
        File(checkNotNull(System.getProperty("user.dir")))
            .resolve("snapshots/studio/$name-$device-${if (dark) "dark" else "light"}.png")
            .canonicalFile
            .also { checkNotNull(it.parentFile).mkdirs() }
            .absolutePath

    private companion object {
        const val SETTLE_MS = 2_000L
        const val TITLE = "Sunday Strands"
        const val CLUE = "Around the house"
        const val SPANGRAM = 5
        const val SIDE_PANE = "create.studio.sidePane"

        /** One word per row, eight rows: the only tiling is the rows themselves; WINDOW spans. */
        val WORDS = listOf("SPRING", "PLANET", "HOLLOW", "BRIGHT", "FLOWER", "WINDOW", "CASTLE", "QUIVER")
    }
}
