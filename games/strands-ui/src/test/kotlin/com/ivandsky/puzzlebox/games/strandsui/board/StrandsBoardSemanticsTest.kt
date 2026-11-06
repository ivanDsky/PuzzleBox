package com.ivandsky.puzzlebox.games.strandsui.board

import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import com.ivandsky.puzzlebox.games.strands.Coord
import com.ivandsky.puzzlebox.games.strands.render.CellState
import com.ivandsky.puzzlebox.games.strands.render.StrandsCell
import com.ivandsky.puzzlebox.games.strandsui.Trace
import com.ivandsky.puzzlebox.games.strandsui.TraceStep
import com.ivandsky.puzzlebox.games.strandsui.VerdictKind
import com.ivandsky.puzzlebox.games.strandsui.WordSlot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** SC12 letter labels, and the buzz each controller outcome earns. */
class StrandsBoardSemanticsTest {

    private fun cell(state: CellState, traceIndex: Int? = null) = StrandsCell(Coord(0, 0), 'S', state, traceIndex)

    @Test
    fun `a letter reads its letter, then selected, then its state`() {
        assertEquals("S", StrandsBoardSemantics.label(cell(CellState.Idle)))
        assertEquals("S, selected", StrandsBoardSemantics.label(cell(CellState.Idle, traceIndex = 0)))
        assertEquals("S, theme", StrandsBoardSemantics.label(cell(CellState.FoundTheme)))
        assertEquals("S, spangram", StrandsBoardSemantics.label(cell(CellState.FoundSpangram)))
        assertEquals("S, hinted", StrandsBoardSemantics.label(cell(CellState.HintOutlined)))
        assertEquals("S, selected, hinted", StrandsBoardSemantics.label(cell(CellState.HintOrderRevealed(2), traceIndex = 1)))
    }

    @Test
    fun `a letter joining ticks, a verdict buzzes by kind, nothing else buzzes`() {
        val one = Trace(listOf(Coord(0, 0)))
        val two = Trace(listOf(Coord(0, 0), Coord(0, 1)))
        val tracing = WordSlot.Tracing("PI")
        fun submit(kind: VerdictKind) =
            StrandsHaptics.forStep(TraceStep.Submit(two.path), two, Trace(), tracing, WordSlot.Verdict("x", kind, seq = 1))

        assertEquals(HapticFeedbackType.TextHandleMove, StrandsHaptics.forStep(TraceStep.Updated(two), one, two, tracing, tracing))
        assertNull(StrandsHaptics.forStep(TraceStep.Updated(one), two, one, tracing, tracing))
        assertNull(StrandsHaptics.forStep(TraceStep.Cleared, two, Trace(), tracing, WordSlot.Empty))
        assertEquals(HapticFeedbackType.Confirm, submit(VerdictKind.Theme))
        assertEquals(HapticFeedbackType.LongPress, submit(VerdictKind.Spangram))
        assertEquals(HapticFeedbackType.Reject, submit(VerdictKind.Reject))
        assertEquals(HapticFeedbackType.TextHandleMove, submit(VerdictKind.NonTheme))
        // A submit the controller ignored (a solved board) leaves the slot as it was.
        val shown = WordSlot.Verdict("x", VerdictKind.Theme, seq = 1)
        assertNull(StrandsHaptics.forStep(TraceStep.Submit(two.path), two, two, shown, shown))
        // An ignored step leaves the trace as it was: no tick.
        assertNull(StrandsHaptics.forStep(TraceStep.Updated(two), one, one, tracing, tracing))
    }
}
