package com.ivandsky.puzzlebox.games.strands.editor

import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.VariantId
import com.ivandsky.puzzlebox.game.editor.Budget
import com.ivandsky.puzzlebox.game.editor.DraftState
import com.ivandsky.puzzlebox.game.editor.GameEditor
import com.ivandsky.puzzlebox.game.editor.Verdict
import com.ivandsky.puzzlebox.games.strands.StrandsDefinition
import com.ivandsky.puzzlebox.model.GameId

/**
 * Strands' pure editor (ADR-0019): one variant, a new board has blank letters, an empty clue and
 * no paths; drafts persist through [StrandsDraftCodec] and analysis is [StrandsDraftAnalyzer].
 */
object StrandsEditor : GameEditor<StrandsDraft> {
    override val gameId: GameId get() = StrandsDefinition.id

    override val variants: List<VariantId> = listOf(StrandsDefinition.VARIANT)

    override val draftFormatVersion: Int get() = StrandsDraftCodec.VERSION

    override fun newDraft(variant: VariantId): StrandsDraft {
        require(variant == StrandsDefinition.VARIANT) { "unknown Strands variant $variant" }
        return StrandsDraft(StrandsDraft.BLANK.toString().repeat(StrandsDraft.ROWS * StrandsDraft.COLS), "", emptyList(), StrandsDraft.NO_SPANGRAM)
    }

    override fun variantOf(draft: StrandsDraft): VariantId = StrandsDefinition.VARIANT

    override fun encodeDraft(state: DraftState<StrandsDraft>): ByteArray = StrandsDraftCodec.encode(state)

    override fun decodeDraft(bytes: ByteArray): DecodeResult<DraftState<StrandsDraft>> = StrandsDraftCodec.decode(bytes)

    override fun analyze(draft: StrandsDraft, budget: Budget): Verdict = StrandsDraftAnalyzer.analyze(draft, budget)
}
