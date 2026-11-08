package com.ivandsky.puzzlebox.game.editor

import com.ivandsky.puzzlebox.game.Command
import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.VariantId
import com.ivandsky.puzzlebox.model.GameId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** Resize keeps content only for an editor with [DraftResize]; either way it is one undo step. */
class EditorDraftResizeTest {

    private fun edited(editor: GameEditor<List<Int>>) =
        EditorDraft.fresh(editor, VariantId("list/n3")).apply(SetAll(listOf(0, 0, 0), listOf(4, 5, 6)))

    @Test
    fun `an editor with DraftResize keeps content on resize`() {
        val draft = edited(KeepingEditor)

        val resized = draft.resize(VariantId("list/n2"))

        assertEquals(listOf(4, 5), resized.state.draft)
    }

    @Test
    fun `an editor without DraftResize gets a blank board on resize`() {
        val draft = edited(BlankEditor)

        val resized = draft.resize(VariantId("list/n2"))

        assertEquals(listOf(0, 0), resized.state.draft)
    }

    @Test
    fun `either resize is one undo step`() {
        for (editor in listOf<GameEditor<List<Int>>>(KeepingEditor, BlankEditor)) {
            val draft = edited(editor)

            val undone = draft.resize(VariantId("list/n2")).undo()

            assertEquals(listOf(4, 5, 6), undone?.state?.draft)
            assertNotNull(undone?.undo())
            assertNull(undone?.undo()?.undo())
        }
    }

    @Test
    fun `clear is a blank board even for an editor with DraftResize`() {
        val cleared = edited(KeepingEditor).clear()

        assertEquals(listOf(0, 0, 0), cleared.state.draft)
    }

    private data class SetAll(val before: List<Int>, val after: List<Int>) : Command<List<Int>> {
        override fun apply(state: List<Int>) = after
        override fun revert(state: List<Int>) = before
    }

    private abstract class ListBase : GameEditor<List<Int>> {
        override val gameId = GameId("list")
        override val variants = listOf(VariantId("list/n2"), VariantId("list/n3"))
        override val draftFormatVersion = 1

        override fun newDraft(variant: VariantId): List<Int> = List(variant.value.removePrefix("list/n").toInt()) { 0 }
        override fun variantOf(draft: List<Int>) = VariantId("list/n${draft.size}")
        override fun encodeDraft(state: DraftState<List<Int>>) = ByteArray(state.draft.size) { state.draft[it].toByte() }
        override fun decodeDraft(bytes: ByteArray): DecodeResult<DraftState<List<Int>>> =
            DecodeResult.Ok(DraftState(bytes.map { it.toInt() }))

        override fun analyze(draft: List<Int>, budget: Budget): Verdict = Verdict.Undetermined

    }

    private object BlankEditor : ListBase()

    private object KeepingEditor : ListBase(), DraftResize<List<Int>> {
        override fun resized(draft: List<Int>, variant: VariantId): List<Int> =
            draft.take(variant.value.removePrefix("list/n").toInt())
    }
}
