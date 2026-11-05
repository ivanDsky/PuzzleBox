package com.ivandsky.puzzlebox.games.strandsui.board

import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import com.ivandsky.puzzlebox.games.strands.render.StrandsCell

/**
 * TalkBack for one letter (SC12): a button read as "<letter>[, selected], <state>", the
 * state word coming from the cell's spec, never a second `when`. Double-tap is a tap.
 * One node per letter: the drawn letter's own text is cleared, so TalkBack stops once and
 * that stop carries the state.
 */
internal object StrandsBoardSemantics {

    fun label(cell: StrandsCell): String = buildString {
        append(cell.letter)
        if (cell.traceIndex != null) append(", ").append(TraceOverlaySpec.stateWord)
        StrandsCellDrawing.specOf(cell.state).stateWord?.let { append(", ").append(it) }
    }

    fun Modifier.letterButton(cell: StrandsCell, onTap: () -> Unit): Modifier = clearAndSetSemantics {
        role = Role.Button
        contentDescription = label(cell)
        onClick {
            onTap()
            true
        }
    }
}
