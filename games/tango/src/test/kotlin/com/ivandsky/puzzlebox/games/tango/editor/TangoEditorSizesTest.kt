package com.ivandsky.puzzlebox.games.tango.editor

import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.VariantId
import com.ivandsky.puzzlebox.game.editor.DraftState
import com.ivandsky.puzzlebox.game.editor.EditorDraft
import com.ivandsky.puzzlebox.game.editor.Solution
import com.ivandsky.puzzlebox.games.tango.TangoCell
import com.ivandsky.puzzlebox.games.tango.TangoEdgeSide
import com.ivandsky.puzzlebox.games.tango.TangoMarker
import com.ivandsky.puzzlebox.games.tango.TangoMarkerKind
import com.ivandsky.puzzlebox.games.tango.codec.TangoCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Tango editor offers 4×4, 6×6, 8×8 and 10×10 (M4-tango-27): 6×6 is preselected, every
 * offered size starts blank, and a resize is one undoable blank board (no `DraftResize`).
 */
class TangoEditorSizesTest {

    private val n4 = VariantId("tango/n4")
    private val n6 = VariantId("tango/n6")
    private val n8 = VariantId("tango/n8")
    private val n10 = VariantId("tango/n10")

    @Test
    fun `the variants are 4, 6, 8 and 10 in that order`() {
        assertEquals(listOf(n4, n6, n8, n10), TangoEditor.variants)
    }

    @Test
    fun `an odd size is not a variant`() {
        assertThrows(IllegalArgumentException::class.java) { TangoEditor.newDraft(VariantId("tango/n7")) }
    }

    @Test
    fun `every variant starts a blank board of its size`() {
        for ((variant, n) in listOf(n4 to 4, n6 to 6, n8 to 8, n10 to 10)) {
            val draft = TangoEditor.newDraft(variant)
            assertEquals("$variant", TangoDraft(n, emptyMap(), emptyList()), draft)
            assertEquals("$variant", variant, TangoEditor.variantOf(draft))
        }
    }

    @Test
    fun `6x6 is the default variant and one of the variants`() {
        assertEquals(n6, TangoEditor.defaultVariant)
        assertTrue(TangoEditor.defaultVariant in TangoEditor.variants)
    }

    @Test
    fun `a resize starts a blank board and undo restores the old one`() {
        val board = TangoDraft(
            6,
            givens = mapOf(0 to TangoCell.SUN, 7 to TangoCell.MOON, 35 to TangoCell.SUN),
            markers = listOf(
                TangoMarker(2, TangoEdgeSide.RIGHT, TangoMarkerKind.SAME),
                TangoMarker(20, TangoEdgeSide.DOWN, TangoMarkerKind.OPPOSITE),
            ),
        )
        val draft = EditorDraft(TangoEditor, DraftState(board))

        val resized = draft.resize(n8)

        assertEquals(TangoDraft(8, emptyMap(), emptyList()), resized.state.draft)
        assertEquals(n8, resized.variant)
        assertEquals(board, checkNotNull(resized.undo()).state.draft)
    }

    @Test
    fun `every size exports TangoCodec bytes of its own variant and width`() {
        for ((variant, n) in listOf(n4 to 4, n6 to 6, n8 to 8, n10 to 10)) {
            val draft = TangoDraft(n, mapOf(0 to TangoCell.SUN, n * n - 1 to TangoCell.MOON), emptyList())

            val puzzle = TangoEditor.toPuzzle(draft, Solution(List(n * n) { 0 }))

            assertEquals("$variant", variant, puzzle.variant)
            val decoded = (TangoCodec.decode(puzzle.payload) as DecodeResult.Ok).value
            assertEquals("$variant", variant, decoded.variant)
            assertEquals("$variant", n, decoded.n)
            assertEquals("$variant", draft.givens, decoded.givens)
        }
    }
}
