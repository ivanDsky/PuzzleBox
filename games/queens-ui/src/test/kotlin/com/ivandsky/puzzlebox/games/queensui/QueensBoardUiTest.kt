package com.ivandsky.puzzlebox.games.queensui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.RecordStats
import com.ivandsky.puzzlebox.common.result.AppResult
import com.ivandsky.puzzlebox.game.ScoreModel
import com.ivandsky.puzzlebox.gameui.CompletionRecordEmitter
import com.ivandsky.puzzlebox.games.queens.Cell
import com.ivandsky.puzzlebox.games.queens.MarkKind
import com.ivandsky.puzzlebox.games.queens.QueensPuzzleRef
import com.ivandsky.puzzlebox.games.queens.QueensSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDialog
import org.robolectric.Shadows
import java.time.LocalDate

// DoD interaction suite (queens-08): every A10–A12/A14 interaction against the real
// session value type driven in-memory (a ref-less play session — the "fake session"),
// JVM/Robolectric only; on-device checks are queens-09.
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w840dp-h1000dp-420dpi")
class QueensBoardUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val n = 4

    private fun setContent(session: QueensSession, score: ScoreModel<QueensSession>? = null): QueensBoardState {
        val state = QueensBoardState(
            initialSession = session,
            score = score,
            emitter = score?.let {
                CompletionRecordEmitter(CoroutineScope(Dispatchers.Unconfined)) {
                    AppResult.Ok(Unit)
                }
            },
        )
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                androidx.compose.foundation.layout.Column {
                    QueensPlayBoard(state, Modifier.fillMaxWidth().testTag(BoardTag))
                    QueensPlayControls(state)
                }
                // R-5: the win overlay presents MODALLY (its own fullscreen dialog
                // window) — mirror the play screen here so win assertions run.
                QueensWinOverlay(state, onPlayAgain = {})
            }
        }
        composeRule.waitForIdle()
        return state
    }

    private val BoardTag = "queens/board"

    /** Tap the fractional center of cell (row, col) on the board node itself. */
    private fun tap(row: Int, col: Int) {
        composeRule.onNodeWithTag(BoardTag).performTouchInput {
            click(Offset(width * (col + 0.5f) / n, height * (row + 0.5f) / n))
        }
        composeRule.waitForIdle()
    }

    /** Defect B repro (fix round 5): the strict cycle CLOSES — tap 3 on the queen cell
     *  removes queen AND mark, back to EMPTY (was: the stored MANUAL X resurfaced). */
    @Test
    fun `tap cycle closes empty to X to queen to empty`() {
        val state = setContent(QueensUiFixtures.session())
        tap(0, 0) // empty → MANUAL X
        assertEquals(MarkKind.MANUAL, state.session.markAt(Cell(0))?.kind)
        tap(0, 0) // X → queen (with A10 auto-fill)
        assertTrue(Cell(0) in state.session.queens)
        assertEquals(MarkKind.AUTO, state.session.markAt(Cell(1))?.kind)
        tap(0, 0) // queen → EMPTY: queen and its stored mark leave together
        assertTrue("queen removed", Cell(0) !in state.session.queens)
        assertNull("cycle closed: the cell is empty, not X", state.session.markAt(Cell(0)))
    }

    @Test
    fun `drag to cross places manual marks idempotently`() {
        val state = setContent(QueensUiFixtures.session())
        composeRule.onNodeWithTag(BoardTag).performTouchInput {
            val y = height * 0.5f / n
            down(Offset(width * 0.5f / n, y))
            moveTo(Offset(width * 1.5f / n, y))
            moveTo(Offset(width * 2.5f / n, y))
            up()
        }
        composeRule.waitForIdle()
        for (col in 0..2) {
            assertEquals("cell $col", MarkKind.MANUAL, state.session.markAt(Cell(col))?.kind)
        }
        val marksBefore = state.session.marks.size
        composeRule.onNodeWithTag(BoardTag).performTouchInput {
            val y = height * 0.5f / n
            down(Offset(width * 0.5f / n, y))
            moveTo(Offset(width * 1.5f / n, y))
            up()
        }
        composeRule.waitForIdle()
        assertEquals("drag over an already-X cell changes nothing", marksBefore, state.session.marks.size)
    }

    /** Strict closure (fix round 5): tapping a placed queen removes it AND its stored
     *  mark — the queen cell ends EMPTY — while MANUAL marks on every other cell
     *  survive untouched. */
    @Test
    fun `tap on a queen removes it and its mark - other MANUAL marks survive`() {
        var session = QueensUiFixtures.session().withCrossToggled(Cell(15), QueensUiFixtures.T0)
        session = session.withQueenPlaced(Cell(1), QueensUiFixtures.T0)
        val state = setContent(session)

        tap(0, 1) // the strict cycle's third state on a queen cell: remove + close
        assertTrue(state.session.queens.isEmpty())
        assertNull(state.session.markAt(Cell(2))) // the removed queen's AUTO mark is gone
        assertNull("the queen's own cell is EMPTY, not X", state.session.markAt(Cell(1)))
        assertEquals(MarkKind.MANUAL, state.session.markAt(Cell(15))?.kind) // other cells untouched
    }

    /** Q-2 regression: lastPlacedQueen clears at tap entry — an ordinary X tap after a
     *  placement must NOT leave the heavy-haptic flag standing. */
    @Test
    fun `ordinary tap after a placement does not leave the heavy haptic flag standing`() {
        val state = setContent(QueensUiFixtures.session())
        state.onTap(0) // MANUAL X
        state.onTap(0) // queen placement sets lastPlacedQueen
        assertNotNull(state.lastPlacedQueen)
        state.onTap(5) // ordinary tap: X on a cell outside the queen's auto-fill
        assertNull(state.lastPlacedQueen)
        assertNull(state.lastRemovedQueen)
    }

    @Test
    fun `undo reverts one action with its side effects`() {
        val state = setContent(QueensUiFixtures.session())
        state.onTap(0) // X
        state.onTap(0) // queen
        state.undo()
        composeRule.waitForIdle()
        assertTrue(state.session.queens.isEmpty())
        assertEquals(MarkKind.MANUAL, state.session.markAt(Cell(0))?.kind)
    }

    @Test
    fun `provenance-aware undo reverts one action with its side effects`() {
        val state = setContent(QueensUiFixtures.session())
        tap(0, 0) // MANUAL X
        tap(0, 0) // queen + AUTO fills
        assertTrue(state.session.marks.any { it.value.kind == MarkKind.AUTO })
        state.undo()
        composeRule.waitForIdle()
        assertTrue(state.session.queens.isEmpty())
        assertEquals("earlier manual mark survives the auto-fill's undo", MarkKind.MANUAL, state.session.markAt(Cell(0))?.kind)
        assertNull(state.session.markAt(Cell(1)))
    }

    @Test
    fun `auto-check toggle gates the violation cue`() {
        val state = setContent(QueensUiFixtures.session())
        state.onTap(1); state.onTap(1) // X then queen at (0,1)
        state.onTap(2) // AUTO X is a mark: one tap places the queen at (0,2) — same row → conflict
        composeRule.waitForIdle()
        state.autoCheck = true
        composeRule.waitForIdle()
        assertTrue(QueensBoardModel.of(state.session, state.hint, state.autoCheck).violations.isNotEmpty())
        state.autoCheck = false
        composeRule.waitForIdle()
        assertTrue(QueensBoardModel.of(state.session, state.hint, state.autoCheck).violations.isEmpty())
    }

    @Test
    fun `hint action surfaces the region highlight`() {
        val state = setContent(QueensUiFixtures.session())
        composeRule.onNodeWithText("Hint").performClick()
        composeRule.waitForIdle()
        assertNotNull(state.hint)
        val model = QueensBoardModel.of(state.session, state.hint, state.autoCheck)
        assertNotNull(model.hintRegion)
        assertTrue(model.hintCells.isNotEmpty())
    }

    /** R-2 (fix round 4): while a violation is live under Auto-check, a one-line
     *  caption near the controls names the rule; it is gone without the toggle or
     *  without a live violation. */
    @Test
    fun `violation live under auto-check surfaces the rule caption`() {
        val state = setContent(QueensUiFixtures.session())
        state.onTap(1); state.onTap(1) // X then queen at (0,1)
        state.onTap(2) // AUTO X is a mark: one tap places the queen at (0,2) — same row → conflict
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("queens/violation_caption").assertDoesNotExist()
        state.autoCheck = true
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("queens/violation_caption").assertExists()
        composeRule.onNodeWithText(
            "Rule conflict — queens can't touch or share a row, column, or region",
        ).assertExists()
        state.autoCheck = false
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("queens/violation_caption").assertDoesNotExist()
    }

    /** R-1 (fix round 4): the armed-move two-tap language is gone — no caption, no
     *  arming; a queen tap removes outright. */
    @Test
    fun `a queen tap removes outright - no armed caption exists`() {
        val state = setContent(QueensUiFixtures.session().withQueenPlaced(Cell(1), QueensUiFixtures.T0))
        composeRule.onNodeWithTag("queens/arm_caption").assertDoesNotExist()
        composeRule.onNodeWithText("Tap a cell to move · tap again to remove").assertDoesNotExist()
        tap(0, 1) // queen → empty, in one tap
        assertTrue(state.session.queens.isEmpty())
    }

    /** R-5 (fix round 5): the win presents as a TRUE MODAL — a fullscreen dialog in
     *  its own window, with back/outside dismissal blocked, so nothing behind it (the
     *  shell's bottom nav included) can be touched and the win state cannot be
     *  silently abandoned mid-celebration (defect A repro). */
    @Test
    fun `win overlay is modal - dialog window with back and outside blocked`() {
        val state = setContent(QueensUiFixtures.session())
        QueensUiFixtures.solutionCells.forEach { state.onTap(it.idx); state.onTap(it.idx) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("queens/win_overlay").assertExists()

        // The overlay presents in its own window: a Dialog is up on the window manager.
        val dialog = ShadowDialog.getLatestDialog()
        assertNotNull("the win presents in a dialog window (modal)", dialog)
        assertTrue("the win dialog is showing", dialog.isShowing)

        // Nothing outside can dismiss it: outside taps are blocked…
        assertFalse(Shadows.shadowOf(dialog).isCancelableOnTouchOutside)
        // …and the back key is consumed without dismissing (compose's dialog back
        // callback no-ops under dismissOnBackPress = false): the win state survives.
        dialog.window!!.decorView.dispatchKeyEvent(
            android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_BACK),
        )
        composeRule.waitForIdle()
        assertTrue("back cannot abandon the win", state.session.isComplete)
        assertTrue("the win dialog is still up after back", dialog.isShowing)
        composeRule.onNodeWithText("Play again").assertExists()
    }

    @Test
    fun `win emits exactly one completion record and shows the banner`() {
        var completions = 0
        val score = object : ScoreModel<QueensSession> {
            override fun completion(session: QueensSession): CompletionRecord {
                completions++
                return CompletionRecord(
                    puzzleRef = QueensPuzzleRef(LocalDate.parse("2026-11-02"), 1),
                    time = 0L,
                    hints = 0,
                    stats = RecordStats(schemaVersion = 1, blob = ""),
                )
            }
        }
        val state = setContent(QueensUiFixtures.session(), score = score)
        // Two taps per solution cell: empty→X, X→queen (AUTO X short-circuits the queen).
        QueensUiFixtures.solutionCells.forEach { state.onTap(it.idx); state.onTap(it.idx) }
        composeRule.waitForIdle()
        assertTrue(state.session.isComplete)
        assertEquals("one record per win transition", 1, completions)
        composeRule.onNodeWithText("Solved!").assertExists()
        // F-3 (fix round 3): the win is no longer a dead end — the banner carries the
        // replay action, and the win is final: Undo is locked out.
        composeRule.onNodeWithText("Play again").assertExists()
        // R-5 (fix round 5): the win overlay presents in its own fullscreen DIALOG
        // window — its bounds cover every window root (the play surface behind it and
        // the modal window itself; no board-pane clipping, no game chrome beside it).
        val overlay = composeRule.onNodeWithTag("queens/win_overlay").fetchSemanticsNode()
        val roots = composeRule.onAllNodes(isRoot()).fetchSemanticsNodes()
        assertTrue("win presents modally — two window roots", roots.size >= 2)
        assertTrue(
            "win overlay covers the window (${overlay.size} vs ${roots.map { it.size }})",
            roots.all { overlay.size.width >= it.size.width && overlay.size.height >= it.size.height },
        )
        composeRule.onNodeWithText("Undo").performClick()
        composeRule.waitForIdle()
        assertTrue("win is final", state.session.isComplete)
    }
}
