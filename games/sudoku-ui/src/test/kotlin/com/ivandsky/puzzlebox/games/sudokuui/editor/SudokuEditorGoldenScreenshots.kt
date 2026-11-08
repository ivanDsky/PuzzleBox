package com.ivandsky.puzzlebox.games.sudokuui.editor

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
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.ivandsky.puzzlebox.common.dispatcher.DispatcherProvider
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.game.editor.Budget
import com.ivandsky.puzzlebox.game.editor.EditorDraft
import com.ivandsky.puzzlebox.game.editor.Verdict
import com.ivandsky.puzzlebox.games.sudoku.SudokuDefinition
import com.ivandsky.puzzlebox.games.sudoku.SudokuGames
import com.ivandsky.puzzlebox.games.sudoku.editor.SudokuDraft
import com.ivandsky.puzzlebox.games.sudoku.editor.SudokuDraftAnalyzer
import com.ivandsky.puzzlebox.games.sudoku.editor.SudokuEditor
import com.ivandsky.puzzlebox.games.sudoku.editor.SudokuForcedCells
import com.ivandsky.puzzlebox.games.sudoku.editor.SudokuPublishGates
import com.ivandsky.puzzlebox.gameui.editor.EditorOverlay
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
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
 * The Sudoku editor board's looks at 6×6 and the debug 9×9, on a 411 dp phone and in the
 * tablet Studio's left pane, light and dark: empty with its fix row, a selected given with
 * the hatched peers, symmetry on with the unpaired cells outlined, Forced cells on, Unique
 * graded, Unique needing guessing, Multiple with witness A on the board, and None with its
 * clashing givens. Every board's verdict comes from [SudokuDraftAnalyzer], asserted before
 * the capture. Recorded with `-Proborazzi.record=true`, verified with
 * `-Proborazzi.verify=true`; goldens in `snapshots/editor/`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-420dpi")
class SudokuEditorGoldenScreenshotsPhone : SudokuEditorGoldenScreenshots("phone")

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w1280dp-h800dp-land-mdpi")
class SudokuEditorGoldenScreenshotsTablet : SudokuEditorGoldenScreenshots("tablet")

abstract class SudokuEditorGoldenScreenshots(private val device: String) {

    @get:Rule
    val rule = createComposeRule()

    @Test fun `empty 6x6 light`() = empty(MINI, dark = false)
    @Test fun `empty 6x6 dark`() = empty(MINI, dark = true)
    @Test fun `empty 9x9 light`() = empty(CLASSIC, dark = false)
    @Test fun `empty 9x9 dark`() = empty(CLASSIC, dark = true)

    @Test fun `selected 6x6 light`() = selected(MINI, dark = false)
    @Test fun `selected 6x6 dark`() = selected(MINI, dark = true)
    @Test fun `selected 9x9 light`() = selected(CLASSIC, dark = false)
    @Test fun `selected 9x9 dark`() = selected(CLASSIC, dark = true)

    @Test fun `symmetry 6x6 light`() = symmetry(MINI, dark = false)
    @Test fun `symmetry 6x6 dark`() = symmetry(MINI, dark = true)
    @Test fun `symmetry 9x9 light`() = symmetry(CLASSIC, dark = false)
    @Test fun `symmetry 9x9 dark`() = symmetry(CLASSIC, dark = true)

    @Test fun `forced 6x6 light`() = forced(MINI, dark = false)
    @Test fun `forced 6x6 dark`() = forced(MINI, dark = true)
    @Test fun `forced 9x9 light`() = forced(CLASSIC, dark = false)
    @Test fun `forced 9x9 dark`() = forced(CLASSIC, dark = true)

    @Test fun `unique 6x6 light`() = unique(MINI, dark = false)
    @Test fun `unique 6x6 dark`() = unique(MINI, dark = true)
    @Test fun `unique 9x9 light`() = unique(CLASSIC, dark = false)
    @Test fun `unique 9x9 dark`() = unique(CLASSIC, dark = true)

    @Test fun `needs guessing 6x6 light`() = needsGuessing(MINI, dark = false)
    @Test fun `needs guessing 6x6 dark`() = needsGuessing(MINI, dark = true)
    @Test fun `needs guessing 9x9 light`() = needsGuessing(CLASSIC, dark = false)
    @Test fun `needs guessing 9x9 dark`() = needsGuessing(CLASSIC, dark = true)

    @Test fun `multiple witness 6x6 light`() = multiple(MINI, dark = false)
    @Test fun `multiple witness 6x6 dark`() = multiple(MINI, dark = true)
    @Test fun `multiple witness 9x9 light`() = multiple(CLASSIC, dark = false)
    @Test fun `multiple witness 9x9 dark`() = multiple(CLASSIC, dark = true)

    @Test fun `none clash 6x6 light`() = none(MINI, dark = false)
    @Test fun `none clash 6x6 dark`() = none(MINI, dark = true)
    @Test fun `none clash 9x9 light`() = none(CLASSIC, dark = false)
    @Test fun `none clash 9x9 dark`() = none(CLASSIC, dark = true)

    private fun empty(game: Game, dark: Boolean) {
        val shown = open(game, givens = "")
        val verdict = analyzed<Verdict.Incomplete>(shown.draft, game)
        capture(game, "empty", dark, shown, verdict, fixList = true)
    }

    private fun selected(game: Game, dark: Boolean) {
        val shown = open(game, game.unique)
        shown.controller.tap(game.selectedCell)
        assertTrue("the selected cell holds a given", shown.draft.givens[game.selectedCell] != 0)
        capture(game, "selected", dark, shown, analyzed<Verdict.Unique>(shown.draft, game))
    }

    private fun symmetry(game: Game, dark: Boolean) {
        val shown = open(game, game.guessing)
        shown.controller.cycleSymmetry()
        assertTrue("some givens have no mirror partner", shown.controller.symmetry.value.unpaired(shown.draft).isNotEmpty())
        capture(game, "symmetry", dark, shown, analyzed<Verdict.Unique>(shown.draft, game))
    }

    private fun forced(game: Game, dark: Boolean) {
        val shown = open(game, game.unique)
        val expected = SudokuForcedCells.of(shown.draft).keys
        assertTrue("logic places some cells", expected.isNotEmpty())
        shown.controller.toggleForcedView()
        capture(game, "forced", dark, shown, analyzed<Verdict.Unique>(shown.draft, game), forcedCells = expected)
    }

    private fun unique(game: Game, dark: Boolean) {
        val shown = open(game, game.unique)
        val verdict = analyzed<Verdict.Unique>(shown.draft, game)
        assertTrue("graded, not guessed", !verdict.needsGuessing && verdict.difficulty != null)
        capture(game, "unique", dark, shown, verdict)
    }

    private fun needsGuessing(game: Game, dark: Boolean) {
        val shown = open(game, game.guessing)
        val verdict = analyzed<Verdict.Unique>(shown.draft, game)
        assertTrue("the hint ladder gives out", verdict.needsGuessing)
        capture(game, "needs-guessing", dark, shown, verdict)
    }

    private fun multiple(game: Game, dark: Boolean) {
        val shown = open(game, game.multiple)
        shown.controller.adoptOverlay(EditorOverlay(EditorOverlay.Witness.A, showOnBoard = true))
        capture(game, "multiple-witness", dark, shown, analyzed<Verdict.Multiple>(shown.draft, game))
    }

    /** Two equal givens in a row: both ring as clashes, before and after the solver's verdict. */
    private fun none(game: Game, dark: Boolean) {
        val shown = open(game, game.clashing)
        val verdict = analyzed<Verdict.None>(shown.draft, game)
        assertTrue("the None board names its clashes", verdict.conflicts.isNotEmpty())
        capture(game, "none-clash", dark, shown, verdict)
    }

    private fun open(game: Game, givens: String): Opened {
        val editor = SudokuEditor(game.definition)
        val blank = EditorDraft.fresh(editor, editor.variants.first())
        val size = blank.state.draft.size
        val cells = if (givens.isEmpty()) List(size * size) { 0 } else grid(givens)
        assertEquals("every cell is spelled out", size * size, cells.size)
        val draft = blank.state.draft.copy(givens = cells)
        val controller = SudokuEditorController(EditorDraft(editor, blank.state.copy(draft = draft))) {}
        return Opened(controller, draft)
    }

    private inline fun <reified V : Verdict> analyzed(draft: SudokuDraft, game: Game): V {
        val verdict = SudokuDraftAnalyzer.analyze(draft, game.definition.variant.geometry, Budget.Unlimited)
        assertTrue("expected ${V::class.simpleName}, got $verdict", verdict is V)
        return verdict as V
    }

    private fun capture(
        game: Game,
        name: String,
        dark: Boolean,
        shown: Opened,
        verdict: Verdict,
        fixList: Boolean = false,
        forcedCells: Set<Int> = emptySet(),
    ) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        val ui = SudokuEditorUi(game.definition)
        val blocks = SudokuPublishGates.blocks(shown.draft, verdict)
        rule.setContent {
            val config = Configuration(LocalConfiguration.current).apply {
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                    if (dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            }
            CompositionLocalProvider(
                LocalConfiguration provides config,
                LocalSudokuEditorDispatchers provides UnconfinedDispatcherProvider,
            ) {
                PuzzleBoxTheme(darkTheme = dark) {
                    Surface(Modifier.testTag(GOLDEN)) {
                        Column(Modifier.padding(Spacing.md).then(frame())) {
                            ui.Board(shown.controller, verdict, Modifier.fillMaxWidth().weight(1f, fill = false))
                            if (fixList) ui.Issues(shown.controller, verdict, blocks, onSelect = {}, Modifier.fillMaxWidth())
                        }
                    }
                }
            }
        }
        rule.waitUntil { forcedTags(game) == forcedCells }
        rule.waitForIdle()
        rule.onNodeWithTag(GOLDEN).captureRoboImage(out(game, name, dark))
    }

    private fun frame(): Modifier =
        if (device == "tablet") Modifier.size(TabletPaneWidth, TabletPaneHeight) else Modifier.width(PhoneWidth)

    private fun forcedTags(game: Game): Set<Int> {
        val size = game.definition.variant.geometry.size
        return (0 until size * size)
            .filter { rule.onAllNodesWithTag("sudoku.editor.cell.$it.forced").fetchSemanticsNodes().isNotEmpty() }
            .toSet()
    }

    private fun out(game: Game, name: String, dark: Boolean): String =
        File(checkNotNull(System.getProperty("user.dir")))
            .resolve("snapshots/editor/$name-${game.label}-$device-${if (dark) "dark" else "light"}.png")
            .canonicalFile
            .also { checkNotNull(it.parentFile).mkdirs() }
            .absolutePath

    private class Opened(val controller: SudokuEditorController, val draft: SudokuDraft)

    /** One game's boards: each string is every cell row-major, `.` for empty. */
    private class Game(
        val definition: SudokuDefinition,
        val label: String,
        val unique: String,
        val guessing: String,
        val multiple: String,
        val clashing: String,
        val selectedCell: Int,
    )

    private object UnconfinedDispatcherProvider : DispatcherProvider {
        override val main: CoroutineDispatcher = Dispatchers.Unconfined
        override val io: CoroutineDispatcher = Dispatchers.Unconfined
        override val default: CoroutineDispatcher = Dispatchers.Unconfined
    }

    private companion object {
        const val GOLDEN = "golden"

        /** The 411 dp phone less the capture's padding. */
        val PhoneWidth = 379.dp

        /** The tablet Studio's left pane (1280 − the 372 dp side pane) less the padding. */
        val TabletPaneWidth = 876.dp
        val TabletPaneHeight = 720.dp

        fun grid(text: String): List<Int> = text.map { if (it == '.') 0 else it.digitToInt() }

        val MINI = Game(
            definition = SudokuGames.MINI,
            label = "6x6",
            unique = "....62..643..2.5.3..1....3...4.65.2.",
            guessing = "..3...2..4.....5.3....46..2....6...1",
            multiple = "..3...2..4.....5.3....46..2........1",
            clashing = "6...62..643..2.5.3..1....3...4.65.2.",
            selectedCell = 4,
        )

        val CLASSIC = Game(
            definition = SudokuGames.CLASSIC,
            label = "9x9",
            unique = "53..7....6..195....98....6.8...6...34..8.3..17...2...6.6....28....419..5....8..79",
            guessing = "8..........36......7..9.2...5...7.......457.....1...3...1....68..85...1..9....4..",
            multiple = "...........36......7..9.2...5...7.......457.....1...3...1....68..85...1..9....4..",
            clashing = "5.5.7....6..195....98....6.8...6...34..8.3..17...2...6.6....28....419..5....8..79",
            selectedCell = 0,
        )
    }
}
