package com.ivandsky.puzzlebox.create.editor

import com.ivandsky.puzzlebox.game.editor.EditorDraft
import com.ivandsky.puzzlebox.game.editor.Solution
import com.ivandsky.puzzlebox.game.editor.Verdict
import com.ivandsky.puzzlebox.gameui.editor.EditorOverlay
import com.ivandsky.puzzlebox.gameui.editor.GameEditorController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.junit.Assert.assertEquals
import org.junit.Test

/** The card's witness choice opens afresh on each new settled Multiple and lives in the open controller. */
class VerdictCardStateTest {

    private var controller = OverlayController()
    private val state = VerdictCardState(recheck = {}, controller = { controller })

    @Test
    fun `a new Multiple opens on the controller's opening overlay and hands it to the board`() {
        state.read(MULTIPLE)

        assertEquals(OPENING, controller.overlay.value)
        assertEquals(listOf(OPENING), controller.adopted)
    }

    @Test
    fun `Checking and the same Multiple again keep the author's choice`() {
        state.read(MULTIPLE)
        val flipped = OPENING.copy(witness = EditorOverlay.Witness.A)
        state.choose(flipped)

        state.read(Verdict.Checking)
        state.read(MULTIPLE)

        assertEquals(flipped, controller.overlay.value)
        assertEquals(listOf(OPENING, flipped), controller.adopted)
    }

    @Test
    fun `a different Multiple resets the choice`() {
        state.read(MULTIPLE)
        state.choose(EditorOverlay())

        state.read(MULTIPLE.copy(differingCells = setOf(1, 2)))

        assertEquals(OPENING, controller.overlay.value)
    }

    @Test
    fun `a choice goes to the board open now, so a fresh board never shows the last board's choice`() {
        state.read(MULTIPLE)
        val last = controller
        controller = OverlayController()

        state.choose(OPENING.copy(witness = EditorOverlay.Witness.A))

        assertEquals(listOf(OPENING), last.adopted)
        assertEquals(OPENING.copy(witness = EditorOverlay.Witness.A), controller.overlay.value)
    }

    private class OverlayController : GameEditorController {
        private val shown = MutableStateFlow(EditorOverlay())
        val adopted = ArrayList<EditorOverlay>()
        override val draft: StateFlow<EditorDraft<*>> get() = error("unused")
        override val overlay: StateFlow<EditorOverlay> = shown.asStateFlow()
        override fun adopt(draft: EditorDraft<*>) = Unit
        override fun adoptOverlay(overlay: EditorOverlay) {
            adopted += overlay
            shown.value = overlay
        }
        override fun openingOverlay(verdict: Verdict): EditorOverlay = OPENING
    }

    private companion object {
        val OPENING = EditorOverlay(EditorOverlay.Witness.B, showOnBoard = true)
        val MULTIPLE = Verdict.Multiple(Solution(listOf(0, 5)), Solution(listOf(1, 4)), setOf(0, 1, 4, 5))
    }
}
