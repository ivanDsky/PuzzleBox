package com.ivandsky.puzzlebox.games.strandsui.editor

import android.content.Context
import android.content.res.Configuration
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import com.ivandsky.puzzlebox.games.strands.editor.StrandsDraft
import com.ivandsky.puzzlebox.games.strands.editor.StrandsDraftAnalyzer
import com.ivandsky.puzzlebox.games.strands.editor.StrandsDraftPath
import com.ivandsky.puzzlebox.games.strands.editor.StrandsEditor
import com.ivandsky.puzzlebox.gameui.editor.EditorOverlay
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
 * The Strands editor's looks, on a 411 dp phone and in the tablet Studio's left pane, light and
 * dark: the blank board, a trace in progress, a selected path with its "+N" handle, pockets, the
 * paths panel with the credit, Unique, Multiple, None and each witness on the board. Every
 * verdict comes from [StrandsDraftAnalyzer] on a real draft, asserted before the capture.
 * Recorded by default, verified with `-Proborazzi.verify=true`; goldens in `snapshots/editor/`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h1400dp-420dpi")
class StrandsEditorGoldenScreenshotsPhone : StrandsEditorGoldenScreenshots("phone")

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w1280dp-h800dp-land-mdpi")
class StrandsEditorGoldenScreenshotsTablet : StrandsEditorGoldenScreenshots("tablet")

abstract class StrandsEditorGoldenScreenshots(private val device: String) {

    @get:Rule
    val rule = createComposeRule()

    @Test fun `blank light`() = blank(dark = false)
    @Test fun `blank dark`() = blank(dark = true)

    @Test fun `trace in progress light`() = tracing(dark = false)
    @Test fun `trace in progress dark`() = tracing(dark = true)

    @Test fun `selected path with extend handle light`() = selected(dark = false)
    @Test fun `selected path with extend handle dark`() = selected(dark = true)

    @Test fun `pockets light`() = pockets(dark = false)
    @Test fun `pockets dark`() = pockets(dark = true)

    @Test fun `paths panel and credit light`() = panel(dark = false)
    @Test fun `paths panel and credit dark`() = panel(dark = true)

    @Test fun `unique light`() = unique(dark = false)
    @Test fun `unique dark`() = unique(dark = true)

    @Test fun `multiple light`() = multiple(dark = false)
    @Test fun `multiple dark`() = multiple(dark = true)

    @Test fun `none light`() = none(dark = false)
    @Test fun `none dark`() = none(dark = true)

    @Test fun `witness a light`() = witness(EditorOverlay.Witness.A, dark = false)
    @Test fun `witness a dark`() = witness(EditorOverlay.Witness.A, dark = true)

    @Test fun `witness b light`() = witness(EditorOverlay.Witness.B, dark = false)
    @Test fun `witness b dark`() = witness(EditorOverlay.Witness.B, dark = true)

    private fun blank(dark: Boolean) {
        val draft = StrandsDraft(BLANK_LETTERS, "", emptyList(), StrandsDraft.NO_SPANGRAM)
        capture("blank", dark, draft, analyzed<Verdict.Incomplete>(draft))
    }

    /** One path down the last row, the next one under the finger. */
    private fun tracing(dark: Boolean) {
        val draft = board(WORDS.takeLast(1), startRow = 7)
        capture("trace", dark, draft, analyzed<Verdict.Incomplete>(draft)) {
            listOf(0, 1, 2, 9, 14).forEach(it::traceTo)
        }
    }

    /** A three-cell path whose typed word is five letters long: the "+2" handle at its end. */
    private fun selected(dark: Boolean) {
        val draft = StrandsDraft(
            letters = BLANK_LETTERS,
            clue = "",
            paths = listOf(StrandsDraftPath(listOf(12, 13, 14), null)),
            spangram = StrandsDraft.NO_SPANGRAM,
        )
        capture("selected-extend", dark, draft, analyzed<Verdict.Incomplete>(draft)) {
            it.selectPath(0)
            it.typeWord("HEART")
        }
    }

    /** Row 0's word starts at its third cell: cells 0 and 1 are too few for any word, a pocket. */
    private fun pockets(dark: Boolean) {
        val base = board(WORDS, startRow = 0)
        val ring = StrandsDraftPath(listOf(2, 3, 4, 5), "RING")
        val draft = base.copy(paths = listOf(ring) + base.paths.drop(1))
        capture("pockets", dark, draft, analyzed<Verdict.Incomplete>(draft))
    }

    /** Phone only: on the tablet the board is all the capture holds, the same as Unique. */
    private fun panel(dark: Boolean) {
        assumeTrue(device == "phone")
        val draft = board(WORDS, startRow = 0).copy(clue = "Around the house", spangram = SPANGRAM)
        capture("paths-panel", dark, draft, analyzed<Verdict.Unique>(draft))
    }

    private fun unique(dark: Boolean) {
        val draft = board(WORDS, startRow = 0).copy(spangram = SPANGRAM)
        capture("unique", dark, draft, analyzed<Verdict.Unique>(draft))
    }

    /** Two STRAND rows over each other zigzag into a second tiling. */
    private fun multiple(dark: Boolean) {
        val draft = twinRows()
        capture("multiple", dark, draft, analyzed<Verdict.Multiple>(draft))
    }

    /** The last row left untraced: no word covers it. */
    private fun none(dark: Boolean) {
        val draft = board(WORDS.dropLast(1), startRow = 0, letterRows = WORDS).copy(spangram = SPANGRAM)
        val verdict = analyzed<Verdict.None>(draft)
        assertTrue("the None board names its clash", verdict.conflicts.isNotEmpty())
        capture("none", dark, draft, verdict)
    }

    private fun witness(witness: EditorOverlay.Witness, dark: Boolean) {
        val draft = twinRows()
        val name = "witness-${witness.name.lowercase()}"
        capture(name, dark, draft, analyzed<Verdict.Multiple>(draft)) {
            it.adoptOverlay(EditorOverlay(witness, showOnBoard = true))
        }
    }

    private fun twinRows(): StrandsDraft =
        board(listOf("STRAND", "STRAND") + WORDS.drop(2), startRow = 0).copy(spangram = SPANGRAM)

    /** One path per row of [words] from [startRow] down; letters of [letterRows] fill the board. */
    private fun board(words: List<String>, startRow: Int, letterRows: List<String> = words): StrandsDraft {
        val letters = CharArray(StrandsDraft.ROWS * StrandsDraft.COLS) { StrandsDraft.BLANK }
        letterRows.forEachIndexed { row, word ->
            word.forEachIndexed { col, letter -> letters[(startRow + row) * StrandsDraft.COLS + col] = letter }
        }
        val paths = words.mapIndexed { row, word ->
            StrandsDraftPath(List(StrandsDraft.COLS) { (startRow + row) * StrandsDraft.COLS + it }, word)
        }
        return StrandsDraft(String(letters), "", paths, StrandsDraft.NO_SPANGRAM)
    }

    private inline fun <reified V : Verdict> analyzed(draft: StrandsDraft): V {
        val verdict = StrandsDraftAnalyzer.analyze(draft, Budget.Unlimited)
        assertTrue("expected ${V::class.simpleName}, got $verdict", verdict is V)
        return verdict as V
    }

    private fun capture(
        name: String,
        dark: Boolean,
        draft: StrandsDraft,
        verdict: Verdict,
        setUp: (StrandsEditorController) -> Unit = {},
    ) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        val controller = StrandsEditorController(EditorDraft(StrandsEditor, DraftState(draft))) {}
        setUp(controller)
        val queue = QueueDispatcher()
        rule.mainClock.autoAdvance = false
        rule.setContent {
            val config = Configuration(LocalConfiguration.current).apply {
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                    if (dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            }
            CompositionLocalProvider(
                LocalConfiguration provides config,
                LocalEditorDispatchers provides QueueDispatcherProvider(queue),
            ) {
                PuzzleBoxTheme(darkTheme = dark) {
                    Surface(Modifier.testTag(GOLDEN)) {
                        Column(Modifier.padding(Spacing.md).width(if (device == "tablet") TabletPaneWidth else PhoneWidth)) {
                            StrandsEditorBoard(controller, Modifier.fillMaxWidth(), verdict)
                        }
                    }
                }
            }
        }
        // The word check runs on the queue, so its line is there before the capture,
        // never racing it.
        rule.mainClock.advanceTimeBy(FRAME_MS)
        queue.runAll()
        // Past the handle's and every mark's settle.
        rule.mainClock.advanceTimeBy(MotionTokens.SettleMillis + SETTLE_SLACK_MS)
        rule.onNodeWithTag(GOLDEN).captureRoboImage(out(name, dark))
    }

    private fun out(name: String, dark: Boolean): String =
        File(checkNotNull(System.getProperty("user.dir")))
            .resolve("snapshots/editor/$name-$device-${if (dark) "dark" else "light"}.png")
            .canonicalFile
            .also { checkNotNull(it.parentFile).mkdirs() }
            .absolutePath

    private companion object {
        const val GOLDEN = "golden"
        const val FRAME_MS = 16L
        const val SETTLE_SLACK_MS = 1_000L
        const val SPANGRAM = 5

        /** The 411 dp phone less the capture's padding. */
        val PhoneWidth = 379.dp

        /** The tablet Studio's left pane (1280 − the 372 dp side pane) less the padding. */
        val TabletPaneWidth = 876.dp

        val BLANK_LETTERS = StrandsDraft.BLANK.toString().repeat(StrandsDraft.ROWS * StrandsDraft.COLS)

        /** One word per row, eight rows: the only tiling is the rows themselves. */
        val WORDS = listOf("SPRING", "PLANET", "HOLLOW", "BRIGHT", "FLOWER", "WINDOW", "CASTLE", "QUIVER")
    }
}
