package com.ivandsky.puzzlebox.games.sudokuui.trainer

import android.content.Context
import android.provider.Settings
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.spotlight.SpotlightOverlay
import com.ivandsky.puzzlebox.game.trainer.TrainerVerdict
import java.io.File
import kotlinx.coroutines.Dispatchers
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The snippet board on a compact phone: a snippet for a single (the cut's givens and the cells the walk filled, the
 * target open), one for an elimination (its notes), the digit picker open under the board, and the reveal over the
 * answered board in the square a round gives it. Rendered with `-Proborazzi.record=true` into `snapshots/`; review
 * evidence, not committed (ADR-0033).
 */
@OptIn(ExperimentalRoborazziApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-420dpi")
class SudokuTrainerSnippetGoldenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val trainer = SudokuTrainerFixtures.trainer

    private fun path(name: String): String = File(checkNotNull(System.getProperty("user.dir")))
        .resolve("snapshots/sudoku-trainer-snippet-$name.png")
        .canonicalFile
        .absolutePath

    private fun noAnimation() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
    }

    /** The single snippet with the most cells on its board (the busiest board a target is read on). */
    private val single get() = SudokuTrainerFixtures.snippets().filter { it.placesDigit }.maxBy { filled(it) }

    /** The elimination snippet of the technique [id] with the most cells filled. */
    private fun elimination(id: String) = SudokuTrainerFixtures.snippets().filter { !it.placesDigit && it.technique.value == id }.maxBy { filled(it) }

    private fun filled(snippet: SudokuSnippet) = snippet.puzzle.geometry.cellCount - SudokuTrainerFixtures.emptyCells(snippet).size

    private fun show(snippet: SudokuSnippet) {
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                Box(Modifier.padding(16.dp)) {
                    SudokuSnippetBoard(snippet, Dispatchers.Default, {}, Modifier.size(328.dp))
                }
            }
        }
        composeRule.waitForIdle()
    }

    /** A round's square: the board with the reveal over it once answered, the way `RoundPane` mounts them. */
    private fun showRound(snippet: SudokuSnippet) {
        composeRule.setContent {
            var answered by remember { mutableStateOf(false) }
            PuzzleBoxTheme(darkTheme = false) {
                Box(Modifier.padding(16.dp).fillMaxWidth()) {
                    Box(Modifier.aspectRatio(1f)) {
                        SudokuSnippetBoard(snippet, Dispatchers.Default, { answered = true }, Modifier.fillMaxSize())
                        if (answered) SpotlightOverlay(remember(snippet) { trainer.spotlight(snippet) })
                    }
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun play(snippet: SudokuSnippet, verdict: TrainerVerdict) {
        val move = checkNotNull(SudokuTrainerFixtures.moveFor(snippet, verdict)) { "${snippet.technique.value} has a $verdict move" }
        composeRule.onNodeWithTag("sudoku.cell.${SudokuTrainerFixtures.cellOf(move)}", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(sudokuPickerKeyTag(SudokuTrainerFixtures.digitOf(move)), useUnmergedTree = true).performClick()
        composeRule.mainClock.advanceTimeBy(1_500)
        composeRule.waitForIdle()
    }

    @Test
    fun `snippet board for a single`() {
        noAnimation()
        show(single)
        composeRule.onRoot().captureRoboImage(path("single"))
    }

    @Test
    fun `snippet board for an elimination, its notes drawn`() {
        noAnimation()
        show(elimination("pointing"))
        composeRule.onRoot().captureRoboImage(path("elimination"))
    }

    @Test
    fun `digit picker open on a single's cell, a popup under the board`() {
        noAnimation()
        val snippet = single
        show(snippet)
        composeRule.onNodeWithTag("sudoku.cell.${SudokuTrainerFixtures.emptyCells(snippet).first()}", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
        captureScreenRoboImage(path("picker-single"))
    }

    @Test
    fun `digit picker open on an elimination's cell, only its own notes`() {
        noAnimation()
        val snippet = elimination("pointing")
        show(snippet)
        val move = checkNotNull(SudokuTrainerFixtures.moveFor(snippet, TrainerVerdict.Right))
        composeRule.onNodeWithTag("sudoku.cell.${SudokuTrainerFixtures.cellOf(move)}", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
        captureScreenRoboImage(path("picker-elimination"))
    }

    @Test
    fun `the reveal over an answered single`() {
        noAnimation()
        val snippet = single
        showRound(snippet)
        play(snippet, TrainerVerdict.Right)
        composeRule.onRoot().captureRoboImage(path("reveal-single"))
    }

    @Test
    fun `the reveal over an answered pointing, the struck notes crossed`() {
        noAnimation()
        val snippet = elimination("pointing")
        showRound(snippet)
        play(snippet, TrainerVerdict.Right)
        composeRule.onRoot().captureRoboImage(path("reveal-pointing"))
    }

    @Test
    fun `the reveal over a wrong digit on a single`() {
        noAnimation()
        val snippet = single
        showRound(snippet)
        play(snippet, TrainerVerdict.Wrong)
        composeRule.onRoot().captureRoboImage(path("reveal-wrong"))
    }
}
