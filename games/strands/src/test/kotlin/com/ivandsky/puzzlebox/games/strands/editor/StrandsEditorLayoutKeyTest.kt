package com.ivandsky.puzzlebox.games.strands.editor

import com.ivandsky.puzzlebox.game.editor.DraftState
import com.ivandsky.puzzlebox.game.editor.EditorDraft
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/** The clue is a publish detail written after the test solve: it never moves the layout key. */
class StrandsEditorLayoutKeyTest {
    private val draft = StrandsEditor.newDraft(StrandsEditor.variants.single()).copy(letters = "A".repeat(StrandsDraft.ROWS * StrandsDraft.COLS))

    private fun key(draft: StrandsDraft): String = EditorDraft(StrandsEditor, DraftState(draft)).layoutKey

    @Test
    fun `a clue edit keeps the layout key`() {
        assertEquals(key(draft), key(draft.copy(clue = "Around the house")))
    }

    @Test
    fun `a letter edit moves the layout key`() {
        assertNotEquals(key(draft), key(draft.copy(letters = "B" + draft.letters.drop(1))))
    }
}
