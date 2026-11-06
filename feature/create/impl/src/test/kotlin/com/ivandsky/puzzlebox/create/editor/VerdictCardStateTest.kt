package com.ivandsky.puzzlebox.create.editor

import com.ivandsky.puzzlebox.game.editor.EditorDraft
import com.ivandsky.puzzlebox.game.editor.Solution
import com.ivandsky.puzzlebox.game.editor.Verdict
import com.ivandsky.puzzlebox.gameui.editor.EditorOverlay
import com.ivandsky.puzzlebox.gameui.editor.GameEditorController
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Test

/** Ticket 31: the card's witness choice opens afresh on each new settled Multiple and reaches the controller. */
class VerdictCardStateTest {

    private val adopted = ArrayList<EditorOverlay>()
    private val controller = object : GameEditorController {
        override val draft: StateFlow<EditorDraft<*>> get() = error("unused")
        override fun adopt(draft: EditorDraft<*>) = Unit
        override fun adoptOverlay(overlay: EditorOverlay) {
            adopted += overlay
        }
        override fun openingOverlay(verdict: Verdict): EditorOverlay = OPENING
    }
    private val state = VerdictCardState(recheck = {}, controller = { controller })

    @Test
    fun `a new Multiple opens on the controller's opening overlay and hands it to the board`() {
        state.read(MULTIPLE)

        assertEquals(OPENING, state.overlay.value)
        assertEquals(listOf(OPENING), adopted)
    }

    @Test
    fun `Checking and the same Multiple again keep the author's choice`() {
        state.read(MULTIPLE)
        val flipped = OPENING.copy(witness = EditorOverlay.Witness.A)
        state.choose(flipped)

        state.read(Verdict.Checking)
        state.read(MULTIPLE)

        assertEquals(flipped, state.overlay.value)
        assertEquals(listOf(OPENING, flipped), adopted)
    }

    @Test
    fun `a different Multiple resets the choice`() {
        state.read(MULTIPLE)
        state.choose(EditorOverlay())

        state.read(MULTIPLE.copy(differingCells = setOf(1, 2)))

        assertEquals(OPENING, state.overlay.value)
    }

    private companion object {
        val OPENING = EditorOverlay(EditorOverlay.Witness.B, showOnBoard = true)
        val MULTIPLE = Verdict.Multiple(Solution(listOf(0, 5)), Solution(listOf(1, 4)), setOf(0, 1, 4, 5))
    }
}
