package com.ivandsky.puzzlebox.games.queensui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.RecordStats
import com.ivandsky.puzzlebox.game.ScoreModel
import com.ivandsky.puzzlebox.games.queens.Cell
import com.ivandsky.puzzlebox.games.queens.MarkKind
import com.ivandsky.puzzlebox.games.queens.QueensPuzzleRef
import com.ivandsky.puzzlebox.games.queens.QueensSession
import com.ivandsky.puzzlebox.games.queens.engine.hints.HintReason
import com.ivandsky.puzzlebox.model.PuzzleKey
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
import java.time.LocalDate

// DoD interaction suite (queens-08): every A10–A12/A14 interaction against the real
// session value type driven in-memory (a ref-less play session — the "fake session"),
// JVM/Robolectric only; on-device checks are queens-09. Fix round 6: ALWAYS-ON
// violations (A14 revised), the Auto-cross session toggle, the staged hint ladder,
// and the win → result-ROUTE handoff (the modal overlay is deleted).
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w840dp-h1000dp-420dpi")
class QueensBoardUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val n = 4

    /** Win records the state holder handed to the (route) callback. */
    private val winRecords = mutableListOf<CompletionRecord>()

    private fun setContent(session: QueensSession, score: ScoreModel<QueensSession>? = null): QueensBoardState {
        winRecords.clear()
        val state = QueensBoardState(
            initialSession = session,
            score = score,
            onWinRecord = { winRecords.add(it) },
        )
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                QueensPlayBoard(state, Modifier.fillMaxWidth().testTag(BoardTag))
                // Fix round 6: no win overlay HERE — the solved state hands off to the
                // result ROUTE at screen level (the app navigates; the board just rests).
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
        state.onTap(10) // ordinary tap: X on a cell outside the queen's auto-fill
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

    /** Fix round 6 (A14 revised): conflict highlighting is ALWAYS ON — the Auto-check
     *  toggle is gone; the stripes are live the moment a conflict is, no setting. */
    @Test
    fun `violations highlight always - no auto-check toggle`() {
        val state = setContent(QueensUiFixtures.session())
        state.onTap(1); state.onTap(1) // X then queen at (0,1)
        state.onTap(2) // AUTO X is a mark: one tap places the queen at (0,2) — same row → conflict
        composeRule.waitForIdle()
        assertTrue(QueensBoardModel.of(state.session).violations.isNotEmpty())
    }

    /** Ticket 08: the rule line moved to the shared caption slot (controller
     *  PlayFeedback.violation) — the controls never grow a conditional caption row, so
     *  the board does not jump when a conflict appears. */
    @Test
    fun `a live conflict adds no caption row to the controls`() {
        val state = setContent(QueensUiFixtures.session())
        state.onTap(1); state.onTap(1) // X then queen at (0,1)
        state.onTap(2) // AUTO X is a mark: one tap places the queen at (0,2) — same row → conflict
        composeRule.waitForIdle()
        assertTrue(QueensBoardModel.of(state.session).violations.isNotEmpty())
        composeRule.onNodeWithTag("queens/violation_caption").assertDoesNotExist()
        composeRule.onNodeWithText(
            "Queens can't touch or share a row, column, or color.",
        ).assertDoesNotExist()
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

    // ------------------------------------------------- fix round 6 (F-4): Auto-cross

    /** A14 revised: the Auto-cross toggle drives the session — OFF strips AUTO marks
     *  from the standing board, ON re-derives them. */
    @Test
    fun `auto-cross toggle strips and re-derives system marks`() {
        val state = setContent(QueensUiFixtures.session())
        tap(0, 0) // X then queen → A10 auto-fill
        tap(0, 0)
        assertTrue(state.session.marks.any { it.value.kind == MarkKind.AUTO })
        state.setAutoCross(false)
        composeRule.waitForIdle()
        assertFalse(state.autoCross)
        assertTrue(
            "OFF leaves queens bare (only the MANUAL stored under the standing queen remains)",
            state.session.marks.values.all { it.kind == MarkKind.MANUAL },
        )
        assertTrue("queens unaffected", Cell(0) in state.session.queens)
        state.setAutoCross(true)
        composeRule.waitForIdle()
        assertTrue("ON re-derives the exclusions", state.session.marks.any { it.value.kind == MarkKind.AUTO })
    }

    /** A14 revised: the flip is a SETTINGS change — not an undo unit; undo reverts the
     *  last BOARD action, not the toggle. */
    @Test
    fun `auto-cross flip is not an undo unit`() {
        val state = setContent(QueensUiFixtures.session())
        tap(0, 0) // X
        state.setAutoCross(false)
        composeRule.waitForIdle()
        state.undo()
        composeRule.waitForIdle()
        // undo reverted the X, not the flip: still OFF, cell 0 empty again
        assertFalse(state.autoCross)
        assertNull(state.session.markAt(Cell(0)))
    }

    // ------------------------------------- fix round 6 (F-2): staged hint ladder

    /** Stage 1: with placed queens proving the forcing, the caption NAMES the row
     *  elimination; a NEW presented hint counts once. */
    @Test
    fun `staged hint stage 1 shows the deduction caption and counts once`() {
        val state = setContent(QueensUiFixtures.session())
        // queens (1,3), (2,0), (3,2) — row 0's queen is forced to cell 1 (ROW elimination)
        listOf(7, 8, 14).forEach { idx -> state.onTap(idx); state.onTap(idx) }
        composeRule.waitForIdle()
        state.requestHint()
        composeRule.waitForIdle()
        val hint = state.hint
        assertTrue("unexpected $hint", hint is com.ivandsky.puzzlebox.games.queens.engine.hints.QueensHint.Region)
        hint as com.ivandsky.puzzlebox.games.queens.engine.hints.QueensHint.Region
        assertEquals(HintReason(HintReason.Kind.ROW, 0), hint.reason)
        assertEquals("one presented hint counts once", 1, state.session.hintsUsed)
        assertNotNull(state.hintCaption)
    }

    /** Stage 2 (the repeat tap): the same forced cell is POINTED at — the crown ghost
     *  target — without counting a second hint. */
    @Test
    fun `staged hint repeat tap points at the forced cell without recounting`() {
        val state = setContent(QueensUiFixtures.session())
        listOf(7, 8, 14).forEach { idx -> state.onTap(idx); state.onTap(idx) }
        state.requestHint()
        composeRule.waitForIdle()
        assertFalse(state.hintPointed)
        state.requestHint()
        composeRule.waitForIdle()
        assertTrue("stage 2 engaged", state.hintPointed)
        assertEquals(1, (state.hint as com.ivandsky.puzzlebox.games.queens.engine.hints.QueensHint.Region).cell.idx)
        assertEquals("the repeat is presentation only", 1, state.session.hintsUsed)
        val model = QueensBoardModel.of(state.session, state.hint, state.hintPointed)
        assertEquals(1, model.hintCell)
    }

    /** The neutral fallback: with NO placed premises the engine has no human-checkable
     *  elimination — the caption stays honest (region named, no deduction claimed). */
    @Test
    fun `staged hint without a human reason falls back to the neutral caption`() {
        val state = setContent(QueensUiFixtures.session())
        state.requestHint()
        composeRule.waitForIdle()
        val hint = state.hint
        assertTrue(hint is com.ivandsky.puzzlebox.games.queens.engine.hints.QueensHint.Region)
        assertNull((hint as com.ivandsky.puzzlebox.games.queens.engine.hints.QueensHint.Region).reason)
        assertEquals("an empty-board hint counts", 1, state.session.hintsUsed)
        assertEquals("Hint — the red region", state.hintCaption)
    }

    /** The Incorrect hint counts too, with its own caption; None never counts. */
    @Test
    fun `incorrect hint surfaces its caption and counts`() {
        val state = setContent(QueensUiFixtures.session())
        state.onTap(0); state.onTap(0) // misplaced queen at (0,0)
        state.requestHint()
        composeRule.waitForIdle()
        assertTrue(state.hint is com.ivandsky.puzzlebox.games.queens.engine.hints.QueensHint.Incorrect)
        assertEquals(1, state.session.hintsUsed)
        assertEquals("Some of these queens can't stay. Take one away.", state.hintCaption)
    }

    /** Any board action clears the staged hint (caption + ghost). */
    @Test
    fun `a board action clears the staged hint`() {
        val state = setContent(QueensUiFixtures.session())
        state.requestHint()
        composeRule.waitForIdle()
        assertNotNull(state.hint)
        state.onTap(5)
        composeRule.waitForIdle()
        assertNull(state.hint)
        assertFalse(state.hintPointed)
        assertNull(state.hintCaption)
    }

    // ------------------------------- fix round 6 (F-3): win → result-route handoff

    /** F-3: the solved transition hands ONE frozen record (time + hints facts) to the
     *  route callback; the modal overlay is deleted — nothing else renders. */
    @Test
    fun `win hands exactly one frozen record to the route callback`() {
        var completions = 0
        val score = object : ScoreModel<QueensSession> {
            override fun completion(session: QueensSession): CompletionRecord {
                completions++
                return CompletionRecord(
                    puzzleRef = QueensPuzzleRef(PuzzleKey.Daily(LocalDate.parse("2026-11-02"), 1)),
                    time = session.elapsedMs,
                    hints = session.hintsUsed,
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
        assertEquals("one handoff to the route callback", 1, winRecords.size)
        assertEquals("frozen hints fact", 0, winRecords.single().hints) // no hint requested
        assertTrue("frozen time fact is the play duration", winRecords.single().time >= 0L)
        // the win is final: undo/redo are locked out and the session stays complete
        assertFalse(state.canUndo)
        state.undo()
        assertTrue("win is final", state.session.isComplete)
        assertEquals("undo after the win left the board alone", QueensUiFixtures.solutionCells.size, state.session.queens.size)
    }

    /** A restored completed session never re-emits — the emission is transition-only. */
    @Test
    fun `restored completed session never re-emits`() {
        var solved = QueensUiFixtures.session()
        QueensUiFixtures.solutionCells.forEach { c -> solved = solved.withQueenPlaced(c, QueensUiFixtures.T0) }
        setContent(solved)
        composeRule.waitForIdle()
        assertTrue("no emission for a restored win", winRecords.isEmpty())
    }

    /** F-1: Clear wipes queens, AUTO and MANUAL marks as ONE undo unit — one undo
     *  reverts to the exact pre-clear board. */
    @Test
    fun `clear wipes everything as one undo unit`() {
        val state = setContent(QueensUiFixtures.session())
        state.onTap(15) // MANUAL X
        tap(0, 0); tap(0, 0) // queen + AUTO fills
        assertTrue(state.session.marks.isNotEmpty())
        state.clearBoard() // the action bar's Clear (QueensPlayUi.clearFor) calls this
        composeRule.waitForIdle()
        assertTrue(state.session.queens.isEmpty())
        assertTrue("MANUAL marks gone too (round-6 ruling)", state.session.marks.isEmpty())
        state.undo()
        composeRule.waitForIdle()
        assertTrue("one undo restores the pre-clear queens", state.session.queens.isNotEmpty())
        assertEquals("one undo restores the pre-clear MANUAL mark", MarkKind.MANUAL, state.session.markAt(Cell(15))?.kind)
    }

    /** Spec A12: a guided board shows a faint queen TalkBack can read on every answer
     *  square, through the ×, until that square's queen lands. A daily shows none. */
    @Test
    fun `a guided board ghosts each answer square until its queen lands`() {
        val state = setContent(QueensUiFixtures.session())
        val answer = QueensUiFixtures.solutionCells.map { it.idx }
        (0 until n * n).forEach { composeRule.onNodeWithTag("queens.ghost.$it").assertDoesNotExist() }

        state.guide()
        composeRule.waitForIdle()
        for (idx in 0 until n * n) {
            val ghost = composeRule.onNodeWithTag("queens.ghost.$idx")
            if (idx in answer) {
                ghost.assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.ContentDescription))
            } else {
                ghost.assertDoesNotExist()
            }
        }

        tap(0, 1) // × on answer square 1: the ghost stays
        composeRule.onNodeWithTag("queens.ghost.1").assertExists()
        tap(0, 1) // the queen lands
        composeRule.onNodeWithTag("queens.ghost.1").assertDoesNotExist()
        composeRule.onNodeWithTag("queens.ghost.7").assertExists()
    }

    /** N1: on a guided board, a queen off the answer shows a corner cue TalkBack reads;
     *  taking the queen away clears it. */
    @Test
    fun `a guided board marks a queen off the answer until it is taken away`() {
        val state = setContent(QueensUiFixtures.session())
        val answer = QueensUiFixtures.solutionCells.map { it.idx }.toSet()
        val stray = (0 until n * n).first { it !in answer && state.session.markAt(Cell(it)) == null }
        state.guide()
        tap(stray / n, stray % n)
        tap(stray / n, stray % n) // ×, then the queen lands off the answer
        composeRule.onNodeWithTag("queens.stray.$stray")
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.ContentDescription))
        tap(stray / n, stray % n) // the queen goes
        composeRule.onNodeWithTag("queens.stray.$stray").assertDoesNotExist()
    }

    /** U2: the next lesson (or a replay) is a new state of the same size; its taps
     *  must reach it, not the state it replaced. */
    @Test
    fun `a new board of the same size takes the taps`() {
        val first = QueensBoardState(initialSession = QueensUiFixtures.session())
        val second = QueensBoardState(initialSession = QueensUiFixtures.session())
        var current by mutableStateOf(first)
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                QueensPlayBoard(current, Modifier.fillMaxWidth().testTag(BoardTag))
            }
        }
        tap(0, 0)
        current = second
        composeRule.waitForIdle()
        tap(0, 0)
        assertEquals(MarkKind.MANUAL, second.session.markAt(Cell(0))?.kind)
        assertEquals(MarkKind.MANUAL, first.session.markAt(Cell(0))?.kind) // untouched by the 2nd tap
    }

    /** V1: a lesson board fills the board pane the tutorial host gives it, edge to edge —
     *  no second gutter inside the board and no 64dp-per-cell cap on a 4×4. */
    @Test
    fun `a lesson board fills the board pane it is given`() {
        val controller = QueensPlayController(
            com.ivandsky.puzzlebox.gameui.PlayEnvironment(
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined),
                onSessionChanged = {},
                onWon = {},
            ),
        )
        val lesson = QueensTutorial.steps.first()
        controller.adoptToday(lesson.puzzle, lesson.ref)
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                androidx.compose.foundation.layout.Box(Modifier.size(379.dp, 600.dp)) {
                    QueensPlayUi().Board(controller, Modifier)
                }
            }
        }
        composeRule.onNodeWithTag("queens.board").assertWidthIsEqualTo(379.dp).assertHeightIsEqualTo(379.dp)
    }
}
