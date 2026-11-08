package com.ivandsky.puzzlebox.games.queensui.editor

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
import com.ivandsky.puzzlebox.games.queens.editor.QueensDraft
import com.ivandsky.puzzlebox.games.queens.editor.QueensDraftAnalyzer
import com.ivandsky.puzzlebox.games.queens.editor.QueensEditor
import com.ivandsky.puzzlebox.games.queens.editor.QueensPublishGates
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
 * 29: the Queens editor board's Flow C looks (rounds 6, 36–40), on a 411 dp phone and in the
 * tablet Studio's left pane, light and dark: Crown mode with crowns and auto-×, each coach
 * line (place k/N, paint, 2 answers, unique), the probe strip Kept and Broken, the ghost
 * crowns with the movable-cell ring, witness A and B on the board, the 75% clash wash under
 * a crown, split stripes and the fix list. Every board is a real 6×6 draft whose verdict
 * comes from [QueensDraftAnalyzer], asserted before the capture.
 * Recorded with `-Proborazzi.record=true`,
 * verified with `-Proborazzi.verify=true`; goldens in `snapshots/editor/`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-420dpi")
class QueensEditorGoldenScreenshotsPhone : QueensEditorGoldenScreenshots("phone")

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w1280dp-h800dp-land-mdpi")
class QueensEditorGoldenScreenshotsTablet : QueensEditorGoldenScreenshots("tablet")

abstract class QueensEditorGoldenScreenshots(private val device: String) {

    @get:Rule
    val rule = createComposeRule()

    @Test fun `crown mode place light`() = placeCrowns(dark = false)
    @Test fun `crown mode place dark`() = placeCrowns(dark = true)

    @Test fun `coach paint light`() = paintRegions(dark = false)
    @Test fun `coach paint dark`() = paintRegions(dark = true)

    @Test fun `coach two answers ghosts light`() = twoAnswers(dark = false)
    @Test fun `coach two answers ghosts dark`() = twoAnswers(dark = true)

    @Test fun `probe kept light`() = probe("probe-kept", setOf(5, 7, 16), dark = false)
    @Test fun `probe kept dark`() = probe("probe-kept", setOf(5, 7, 16), dark = true)

    @Test fun `probe broken light`() = probe("probe-broken", setOf(5, 8), dark = false)
    @Test fun `probe broken dark`() = probe("probe-broken", setOf(5, 8), dark = true)

    @Test fun `witness a light`() = witness(EditorOverlay.Witness.A, dark = false)
    @Test fun `witness a dark`() = witness(EditorOverlay.Witness.A, dark = true)

    @Test fun `witness b light`() = witness(EditorOverlay.Witness.B, dark = false)
    @Test fun `witness b dark`() = witness(EditorOverlay.Witness.B, dark = true)

    @Test fun `clash light`() = clash(dark = false)
    @Test fun `clash dark`() = clash(dark = true)

    @Test fun `split stripes and fix list light`() = split(dark = false)
    @Test fun `split stripes and fix list dark`() = split(dark = true)

    private fun placeCrowns(dark: Boolean) {
        val draft = QueensDraft(SIZE, BLANK, crowns = setOf(1, 9, 17))
        capture("crown-mode-place", dark, draft, analyzed<Verdict.Incomplete>(draft), QueensEditorMode.Crown)
    }

    private fun paintRegions(dark: Boolean) {
        val draft = QueensDraft(SIZE, BLANK, crowns = UNIQUE_SOLUTION)
        capture("coach-paint", dark, draft, analyzed<Verdict.Incomplete>(draft), QueensEditorMode.Crown)
    }

    private fun twoAnswers(dark: Boolean) {
        val draft = QueensDraft(SIZE, MULTIPLE, crowns = setOf(1, 9, 12, 22))
        capture("coach-two-answers", dark, draft, analyzed<Verdict.Multiple>(draft), QueensEditorMode.Crown)
    }

    private fun probe(name: String, crowns: Set<Int>, dark: Boolean) {
        val draft = QueensDraft(SIZE, UNIQUE, crowns = crowns)
        capture(name, dark, draft, analyzed<Verdict.Unique>(draft), QueensEditorMode.Crown)
    }

    private fun witness(witness: EditorOverlay.Witness, dark: Boolean) {
        val draft = QueensDraft(SIZE, MULTIPLE)
        val name = "witness-${witness.name.lowercase()}"
        capture(name, dark, draft, analyzed<Verdict.Multiple>(draft), overlay = EditorOverlay(witness, showOnBoard = true))
    }

    /** A crown on a clash cell: the wash sits under it, the crown keeps its ink. */
    private fun clash(dark: Boolean) {
        val verdict = analyzed<Verdict.None>(QueensDraft(SIZE, NONE))
        assertTrue("the None board names its clash", verdict.conflicts.isNotEmpty())
        capture("clash", dark, QueensDraft(SIZE, NONE, crowns = setOf(verdict.conflicts.min())), verdict)
    }

    /** Region E's stray piece at cell 0, and a double crown in region A, with the fix list under the board. */
    private fun split(dark: Boolean) {
        val draft = QueensDraft(SIZE, UNIQUE.toMutableList().apply { this[0] = REGION_E }, crowns = setOf(4, 5))
        capture("split-fix-list", dark, draft, analyzed(draft), fixList = true)
    }

    private inline fun <reified V : Verdict> analyzed(draft: QueensDraft): V {
        val verdict = QueensDraftAnalyzer.analyze(draft, Budget.Unlimited)
        assertTrue("expected ${V::class.simpleName}, got $verdict", verdict is V)
        return verdict as V
    }

    private fun capture(
        name: String,
        dark: Boolean,
        draft: QueensDraft,
        verdict: Verdict,
        mode: QueensEditorMode? = null,
        overlay: EditorOverlay? = null,
        fixList: Boolean = false,
    ) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        val controller = QueensEditorController(EditorDraft(QueensEditor, DraftState(draft))) {}
        mode?.let(controller::setMode)
        overlay?.let(controller::adoptOverlay)
        val blocks = QueensPublishGates.blocks(draft, verdict)
        rule.mainClock.autoAdvance = false
        rule.setContent {
            val config = Configuration(LocalConfiguration.current).apply {
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                    if (dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            }
            CompositionLocalProvider(LocalConfiguration provides config) {
                PuzzleBoxTheme(darkTheme = dark) {
                    Surface(Modifier.testTag(GOLDEN)) {
                        Column(Modifier.padding(Spacing.md).then(if (device == "tablet") Modifier.size(TabletPaneWidth, TabletPaneHeight) else Modifier.width(PhoneWidth))) {
                            QueensEditorBoard(controller, verdict, if (fixList) Modifier.fillMaxWidth().weight(1f, fill = false) else Modifier.fillMaxWidth())
                            if (fixList) QueensFixList(controller, verdict, blocks, onSelect = {}, Modifier.fillMaxWidth())
                        }
                    }
                }
            }
        }
        // Past the split stripes' settle pause and every mark's fade-in.
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
        const val SIZE = 6
        const val REGION_E = 4
        const val SETTLE_SLACK_MS = 1_000L

        /** The 411 dp phone less the capture's padding. */
        val PhoneWidth = 379.dp

        /** The tablet Studio's left pane (1280 − the 372 dp side pane) less the padding. */
        val TabletPaneWidth = 876.dp
        val TabletPaneHeight = 720.dp

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
