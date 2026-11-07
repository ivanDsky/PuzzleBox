package com.ivandsky.puzzlebox.games.sudoku.editor

import com.ivandsky.puzzlebox.game.DecodeError
import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.VariantId
import com.ivandsky.puzzlebox.game.editor.Budget
import com.ivandsky.puzzlebox.game.editor.DraftState
import com.ivandsky.puzzlebox.game.editor.GameEditor
import com.ivandsky.puzzlebox.game.editor.Verdict
import com.ivandsky.puzzlebox.games.sudoku.SudokuDefinition
import com.ivandsky.puzzlebox.model.GameId

/**
 * The pure editor of one Sudoku game: its only variant is the [definition]'s, so a draft of
 * another size decodes to [DecodeError.VariantSizeMismatch] rather than opening under the wrong game.
 */
class SudokuEditor(private val definition: SudokuDefinition) : GameEditor<SudokuDraft> {
    private val variant = definition.variant

    override val gameId: GameId get() = definition.id

    override val variants: List<VariantId> = listOf(variant.id)

    override val draftFormatVersion: Int get() = SudokuDraftCodec.VERSION

    override fun newDraft(variant: VariantId): SudokuDraft {
        require(variant == this.variant.id) { "unknown variant $variant for ${definition.id}" }
        val size = this.variant.geometry.size
        return SudokuDraft(size, List(size * size) { 0 })
    }

    override fun variantOf(draft: SudokuDraft): VariantId {
        require(draft.size == variant.geometry.size) { "a ${draft.size}×${draft.size} draft is not ${variant.id}" }
        return variant.id
    }

    override fun encodeDraft(state: DraftState<SudokuDraft>): ByteArray = SudokuDraftCodec.encode(state)

    override fun decodeDraft(bytes: ByteArray): DecodeResult<DraftState<SudokuDraft>> =
        when (val decoded = SudokuDraftCodec.decode(bytes)) {
            is DecodeResult.Err -> decoded
            is DecodeResult.Ok -> {
                val size = decoded.value.draft.size
                val expected = variant.geometry.size
                if (size == expected) {
                    decoded
                } else {
                    DecodeResult.Err(DecodeError.VariantSizeMismatch(variant.id.value, expected, size))
                }
            }
        }

    override fun analyze(draft: SudokuDraft, budget: Budget): Verdict =
        SudokuDraftAnalyzer.analyze(draft, variant.geometry, budget)
}
