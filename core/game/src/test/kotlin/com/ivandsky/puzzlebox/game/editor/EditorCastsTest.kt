package com.ivandsky.puzzlebox.game.editor

import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.VariantId
import com.ivandsky.puzzlebox.model.GameId
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

/** [narrowTo] and [capability] hand back typed views only for the editor (or capability) that is really there. */
class EditorCastsTest {

    private val variant = VariantId("list/n2")

    @Test
    fun `narrowTo the draft's own editor returns the same handle`() {
        val draft = EditorDraft.fresh(PlainEditor, variant)

        val erased: EditorDraft<*> = draft

        assertSame(draft, erased.narrowTo(PlainEditor))
    }

    @Test
    fun `narrowTo another editor is null`() {
        val draft: EditorDraft<*> = EditorDraft.fresh(PlainEditor, variant)

        assertNull(draft.narrowTo(OtherEditor))
    }

    @Test
    fun `narrowTo a class accepts instances only`() {
        val draft: EditorDraft<*> = EditorDraft.fresh(PlainEditor, variant)

        assertNotNull(draft.narrowTo(PlainEditor::class))
        assertNull(draft.narrowTo(OtherEditor::class))
    }

    @Test
    fun `narrowTo a class accepts another instance of that class`() {
        val draft: EditorDraft<*> = EditorDraft.fresh(SizedEditor(2), variant)

        assertNotNull(draft.narrowTo(SizedEditor::class))
        assertNull(draft.narrowTo(SizedEditor(3)))
    }

    @Test
    fun `capability is the editor when implemented, else null`() {
        assertNotNull(ResizingEditor.capability<List<Int>, DraftResize<List<Int>>>())
        assertNull(PlainEditor.capability<List<Int>, DraftResize<List<Int>>>())
    }

    private abstract class ListBase : GameEditor<List<Int>> {
        override val gameId = GameId("list")
        override val variants = listOf(VariantId("list/n2"))
        override val draftFormatVersion = 1

        override fun newDraft(variant: VariantId): List<Int> = listOf(0, 0)
        override fun variantOf(draft: List<Int>) = VariantId("list/n2")
        override fun encodeDraft(state: DraftState<List<Int>>) = ByteArray(0)
        override fun decodeDraft(bytes: ByteArray): DecodeResult<DraftState<List<Int>>> =
            DecodeResult.Ok(DraftState(listOf(0, 0)))

        override fun analyze(draft: List<Int>, budget: Budget): Verdict = Verdict.Undetermined
    }

    private class SizedEditor(val size: Int) : ListBase()

    private object PlainEditor : ListBase()

    private object OtherEditor : ListBase()

    private object ResizingEditor : ListBase(), DraftResize<List<Int>> {
        override fun resized(draft: List<Int>, variant: VariantId) = draft
    }
}
