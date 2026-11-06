package com.ivandsky.puzzlebox.games.queens.editor

import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.VariantId
import com.ivandsky.puzzlebox.game.editor.Budget
import com.ivandsky.puzzlebox.game.editor.DraftState
import com.ivandsky.puzzlebox.game.editor.GameEditor
import com.ivandsky.puzzlebox.game.editor.PublishBlock
import com.ivandsky.puzzlebox.game.editor.Verdict
import com.ivandsky.puzzlebox.games.queens.QueensDefinition
import com.ivandsky.puzzlebox.games.queens.QueensVariants
import com.ivandsky.puzzlebox.games.queens.queensVariantId
import com.ivandsky.puzzlebox.model.GameId

/**
 * Queens' pure editor (ADR-0019): a new board is one region, every cell 0 (A2), so it
 * starts Incomplete; the variant table is the shipped sizes 4..12 (A5).
 */
object QueensEditor : GameEditor<QueensDraft> {
    override val gameId: GameId get() = QueensDefinition.id

    override val variants: List<VariantId> = QueensVariants.all.map { it.id }

    override val draftFormatVersion: Int get() = QueensDraftCodec.VERSION

    override fun newDraft(variant: VariantId): QueensDraft {
        val size = requireNotNull(QueensVariants.byId(variant)) { "unknown Queens variant $variant" }.size
        return QueensDraft(size, List(size * size) { 0 })
    }

    override fun variantOf(draft: QueensDraft): VariantId = queensVariantId(draft.size)

    override fun encodeDraft(state: DraftState<QueensDraft>): ByteArray = QueensDraftCodec.encode(state)

    override fun decodeDraft(bytes: ByteArray): DecodeResult<DraftState<QueensDraft>> = QueensDraftCodec.decode(bytes)

    override fun analyze(draft: QueensDraft, budget: Budget): Verdict = QueensDraftAnalyzer.analyze(draft, budget)

    override fun publishBlocks(draft: QueensDraft, verdict: Verdict): List<PublishBlock> =
        QueensPublishGates.blocks(draft, verdict)
}
