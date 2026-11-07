package com.ivandsky.puzzlebox.games.queens.editor

import com.ivandsky.puzzlebox.game.editor.DraftState
import com.ivandsky.puzzlebox.game.editor.EditorDraft
import com.ivandsky.puzzlebox.games.queens.editor.QueensDraft.Companion.UNPAINTED
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Answer crowns never enter [EditorDraft.layoutKey] ([QueensEditor.layoutOf]): a test solve
 * recorded against the key (ValidatePublish compares `testSolve.layoutKey == draft.layoutKey`)
 * survives placing, moving and removing crowns, while a paint invalidates it.
 */
class QueensLayoutKeyTest {

    private val board = QueensDraft(
        4,
        listOf(
            0, 0, 1, 1,
            0, 2, 2, 1,
            3, 2, 2, 1,
            3, 3, UNPAINTED, 1,
        ),
    )
    private val draft = EditorDraft(QueensEditor, DraftState(board))

    /** The key a test solve on [draft] records (`TestSolve.layoutKey`). */
    private val solvedKey = draft.layoutKey

    private fun EditorDraft<QueensDraft>.crownTap(cell: Int): EditorDraft<QueensDraft> =
        apply(QueensCrowns.toggle(state.draft, cell)!!)

    @Test
    fun `placing, moving and removing a crown keep the layout key`() {
        val placed = draft.crownTap(1)
        assertEquals(setOf(1), placed.state.draft.crowns)
        assertEquals(solvedKey, placed.layoutKey)

        val lifted = placed.crownTap(1)
        val moved = lifted.crownTap(4)
        assertEquals(setOf(4), moved.state.draft.crowns)
        assertEquals(solvedKey, lifted.layoutKey)
        assertEquals(solvedKey, moved.layoutKey)

        val removed = moved.crownTap(4)
        assertEquals(emptySet<Int>(), removed.state.draft.crowns)
        assertEquals(solvedKey, removed.layoutKey)
    }

    @Test
    fun `the layout key ignores crowns although the draft bytes carry them`() {
        val crowned = EditorDraft(QueensEditor, DraftState(board.copy(crowns = setOf(1, 11))))

        assertNotEquals(draft.encode().toList(), crowned.encode().toList())
        assertEquals(solvedKey, crowned.layoutKey)
    }

    @Test
    fun `painting one cell changes the layout key`() {
        val painted = draft.crownTap(1).apply(board.paint(setOf(14), 3))

        assertNotEquals(solvedKey, painted.layoutKey)
    }

    @Test
    fun `a crown seeding an unpainted cell paints it and so changes the layout key`() {
        val seeded = draft.crownTap(14)

        assertEquals(0, seeded.state.draft.regions[14])
        assertNotEquals(solvedKey, seeded.layoutKey)
        assertEquals(solvedKey, seeded.undo()!!.layoutKey)
    }
}
