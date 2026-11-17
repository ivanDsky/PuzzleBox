package com.ivandsky.puzzlebox.games.queens.editor

import com.ivandsky.puzzlebox.game.editor.Budget
import com.ivandsky.puzzlebox.game.editor.Completion
import com.ivandsky.puzzlebox.game.editor.Difficulty.Level
import com.ivandsky.puzzlebox.game.editor.DraftState
import com.ivandsky.puzzlebox.game.editor.EditorDraft
import com.ivandsky.puzzlebox.games.queens.editor.QueensDraft.Companion.UNPAINTED
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import kotlin.random.Random

/**
 * [QueensDraft.recoloured] (ticket 107): a finish that had to change the colour of painted cells
 * hands those cells back for the editor to highlight. The marks live until the author's next
 * paint or crown edit, and are never saved or part of the layout key.
 */
class QueensRecolouredTest {

    @Test
    fun `a finish that had to recolour marks exactly the painted cells whose colour changed`() {
        val done = (QueensCompleter.complete(REPORTED, Random(1), Budget.Unlimited, Level.Hard) as Completion.Done).draft

        val changed = REPORTED.regions.indices.filter { REPORTED.regions[it] != done.regions[it] }.toSet()
        assertEquals("the reported board is repaired in one cell", 1, changed.size)
        assertEquals(changed, done.recoloured)
        assertEquals(1, QueensCompleter.changedPieces(done))
    }

    @Test
    fun `a finish that keeps the paint marks nothing, and cells it filled are not marked`() {
        val draft = QueensDraft(4, PARTIAL)

        val done = (QueensCompleter.complete(draft, Random(1), Budget.Unlimited, Level.Easy) as Completion.Done).draft

        assertEquals(PARTIAL.filter { it != UNPAINTED }, done.regions.filterIndexed { cell, _ -> PARTIAL[cell] != UNPAINTED })
        assertEquals(emptySet<Int>(), done.recoloured)
        assertEquals(0, QueensCompleter.changedPieces(done))
    }

    @Test
    fun `a paint clears the marks and undoing it does not bring them back`() {
        val marked = QueensDraft(4, WHOLE, crowns = setOf(1), recoloured = setOf(5, 6))
        val paint = marked.paint(setOf(0), 1)

        val painted = paint.apply(marked)

        assertEquals(emptySet<Int>(), painted.recoloured)
        assertEquals(marked.copy(recoloured = emptySet()), paint.revert(painted))
    }

    @Test
    fun `placing and lifting a crown clear the marks`() {
        val marked = QueensDraft(4, WHOLE, crowns = setOf(1), recoloured = setOf(5, 6))
        val place = QueensDraftCommand.Crown(7, placed = true, seed = null)
        val lift = QueensDraftCommand.Crown(1, placed = false, seed = null)

        assertEquals(emptySet<Int>(), place.apply(marked).recoloured)
        assertEquals(marked.copy(recoloured = emptySet()), place.revert(place.apply(marked)))
        assertEquals(emptySet<Int>(), lift.apply(marked).recoloured)
        assertEquals(marked.copy(recoloured = emptySet()), lift.revert(lift.apply(marked)))
    }

    @Test
    fun `the marks are never saved and never part of the layout key`() {
        val plain = QueensDraft(4, WHOLE, crowns = setOf(1))
        val marked = plain.copy(recoloured = setOf(5, 6))

        assertArrayEquals(QueensEditor.encodeDraft(DraftState(plain)), QueensEditor.encodeDraft(DraftState(marked)))
        assertEquals(EditorDraft(QueensEditor, DraftState(plain)).layoutKey, EditorDraft(QueensEditor, DraftState(marked)).layoutKey)
        assertEquals(emptySet<Int>(), QueensEditor.layoutOf(marked).recoloured)
    }

    @Test
    fun `a mark off the board is refused`() {
        assertThrows(IllegalArgumentException::class.java) { QueensDraft(4, WHOLE, recoloured = setOf(16)) }
    }

    private companion object {
        val WHOLE = listOf(
            0, 0, 1, 1,
            0, 2, 2, 1,
            3, 2, 2, 1,
            3, 3, 3, 1,
        )

        val PARTIAL = listOf(
            0, 0, 1, 1,
            0, UNPAINTED, UNPAINTED, 1,
            3, 2, 2, 1,
            3, 3, UNPAINTED, 1,
        )

        /** The board of the user report (build 1148), as in `QueensCompleterRepaintTest`: every cell painted, seven crowns, six answers. */
        val REPORTED = QueensDraft(
            size = 7,
            regions = listOf("TBBBBOO", "TRRRROO", "TTTRRDO", "TTRRDDD", "TTRYYGG", "TTRYGGG", "TTYYGGG").flatMap { row -> row.map { "TBRODYG".indexOf(it) } },
            crowns = setOf(3, 8, 20, 25, 28, 40, 44),
        )
    }
}
