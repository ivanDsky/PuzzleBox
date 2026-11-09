package com.ivandsky.puzzlebox.game.editor

import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.game.VariantId
import com.ivandsky.puzzlebox.model.GameId
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A draft reports the version its own payload is written in ([EditorShareCodec.shareFormatVersionOf]),
 * not the codec's max: a one-cell board stays on v1 so older builds still open it, while every
 * other size needs v2. The editor takes the codec `by` delegation, so the override reaches it unedited.
 */
class EditorDraftShareTest {

    @Test
    fun `a draft's share version is the version of its own payload`() {
        val draft = EditorDraft.fresh(SplitEditor, VariantId("list/n1"))

        assertEquals(OLD_VERSION, draft.shareFormatVersion)
    }

    @Test
    fun `a draft the old version cannot carry reports the newer version`() {
        val draft = EditorDraft.fresh(SplitEditor, VariantId("list/n2"))

        assertEquals(MAX_VERSION, draft.shareFormatVersion)
    }

    /** Writes [OLD_VERSION] for one-cell boards and [MAX_VERSION] for the rest. */
    private object SplitShareCodec : EditorShareCodec<List<Int>> {
        override val shareFormatVersion = MAX_VERSION

        override fun shareFormatVersionOf(draft: List<Int>): Int = if (draft.size == 1) OLD_VERSION else MAX_VERSION

        override fun encodeShare(draft: List<Int>): ByteArray =
            byteArrayOf(shareFormatVersionOf(draft).toByte(), *ByteArray(draft.size) { draft[it].toByte() })

        override fun decodeShare(bytes: ByteArray): DecodeResult<List<Int>> = DecodeResult.Ok(bytes.drop(1).map { it.toInt() })
    }

    private object ListExport : DraftExport<List<Int>> {
        override fun toPuzzle(draft: List<Int>, solution: Solution) =
            Puzzle(GameId("list"), VariantId("list/n${draft.size}"), 1, draft.map { it.toByte() }.toByteArray())
    }

    private object SplitEditor :
        GameEditor<List<Int>>,
        EditorShareCodec<List<Int>> by SplitShareCodec,
        DraftExport<List<Int>> by ListExport {
        override val gameId = GameId("list")
        override val variants = listOf(VariantId("list/n1"), VariantId("list/n2"))
        override val draftFormatVersion = 1

        override fun newDraft(variant: VariantId): List<Int> = List(variant.value.removePrefix("list/n").toInt()) { 0 }
        override fun variantOf(draft: List<Int>) = VariantId("list/n${draft.size}")
        override fun encodeDraft(state: DraftState<List<Int>>) = ByteArray(state.draft.size) { state.draft[it].toByte() }
        override fun decodeDraft(bytes: ByteArray): DecodeResult<DraftState<List<Int>>> =
            DecodeResult.Ok(DraftState(bytes.map { it.toInt() }))

        override fun analyze(draft: List<Int>, budget: Budget): Verdict = Verdict.Undetermined
    }

    private companion object {
        const val OLD_VERSION = 1
        const val MAX_VERSION = 2
    }
}
