package com.ivandsky.puzzlebox.games.queensui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
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

    @Test
    fun `tap cycle empty to X to queen with A10 auto-fill`() {
        val state = setContent(QueensUiFixtures.session())
        tap(0, 0)
        assertEquals(MarkKind.MANUAL, state.session.markAt(Cell(0))?.kind)
        tap(0, 0)
        assertTrue(Cell(0) in state.session.queens)
        assertEquals(MarkKind.AUTO, state.session.markAt(Cell(1))?.kind)
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

    @Test
    fun `queen move clears AUTO marks only - MANUAL survives`() {
        var session = QueensUiFixtures.session().withCrossToggled(Cell(15), QueensUiFixtures.T0)
        session = session.withQueenPlaced(Cell(1), QueensUiFixtures.T0)
        val state = setContent(session)

        tap(0, 1) // select the queen
        assertEquals(Cell(1), state.pendingMoveCell)
        tap(2, 0) // move to cell 8 (a solution cell)
        assertEquals(listOf(Cell(8)), state.session.queens)
        assertNull(state.session.markAt(Cell(1))) // moved queen's AUTO marks gone
        assertEquals(MarkKind.MANUAL, state.session.markAt(Cell(15))?.kind) // MANUAL survives
    }

    @Test
    fun `repeat tap on the selected queen removes it`() {
        val session = QueensUiFixtures.session().withQueenPlaced(Cell(1), QueensUiFixtures.T0)
        val state = setContent(session)
        tap(0, 1) // select
        tap(0, 1) // remove — the cycle's third state
        assertTrue(state.session.queens.isEmpty())
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

    /** Q-1 regression: undoing a queen MOVE (equal queen counts) sets the §5.4
     *  crossfade-back pair; undoing a placement does not. */
    @Test
    fun `undoing a queen move surfaces the restored move pair`() {
        val state = setContent(QueensUiFixtures.session())
        state.onTap(1); state.onTap(1) // X then queen at (0,1)
        state.onTap(1) // select the queen for the A11 move
        state.onTap(8) // move to (2,0)
        assertEquals(listOf(Cell(8)), state.session.queens)
        state.undo()
        composeRule.waitForIdle()
        assertEquals(8 to 1, state.lastUndoRestoredMove)
    }

    @Test
    fun `undoing a placement does not surface a restored move`() {
        val state = setContent(QueensUiFixtures.session())
        state.onTap(0) // X
        state.onTap(0) // queen
        state.undo()
        composeRule.waitForIdle()
        assertNull(state.lastUndoRestoredMove)
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
        state.onTap(2); state.onTap(2) // AUTO X then queen at (0,2) — same row → conflict
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
        // The win is final: Undo is locked out.
        composeRule.onNodeWithText("Undo").performClick()
        composeRule.waitForIdle()
        assertTrue("win is final", state.session.isComplete)
    }
}
