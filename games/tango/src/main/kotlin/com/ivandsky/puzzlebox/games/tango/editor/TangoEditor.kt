package com.ivandsky.puzzlebox.games.tango.editor

import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.VariantId
import com.ivandsky.puzzlebox.game.editor.Budget
import com.ivandsky.puzzlebox.game.editor.DraftState
import com.ivandsky.puzzlebox.game.editor.GameEditor
import com.ivandsky.puzzlebox.game.editor.Verdict
import com.ivandsky.puzzlebox.games.tango.TangoDefinition
import com.ivandsky.puzzlebox.model.GameId

/**
 * Tango's pure editor (ADR-0019): one 6×6 variant, a new board has no givens and no markers,
 * drafts persist through [TangoDraftCodec] and analysis is [TangoDraftAnalyzer].
 */
object TangoEditor : GameEditor<TangoDraft> {
    private val N6 = VariantId("tango/n${TangoDraft.N}")

    override val gameId: GameId get() = TangoDefinition.id

    override val variants: List<VariantId> = listOf(N6)

    override val draftFormatVersion: Int get() = TangoDraftCodec.VERSION

    override fun newDraft(variant: VariantId): TangoDraft {
        require(variant == N6) { "unknown Tango variant $variant" }
        return TangoDraft(emptyMap(), emptyList())
    }

    override fun variantOf(draft: TangoDraft): VariantId = N6

    override fun encodeDraft(state: DraftState<TangoDraft>): ByteArray = TangoDraftCodec.encode(state)

    override fun decodeDraft(bytes: ByteArray): DecodeResult<DraftState<TangoDraft>> = TangoDraftCodec.decode(bytes)

    override fun analyze(draft: TangoDraft, budget: Budget): Verdict = TangoDraftAnalyzer.analyze(draft, budget)
}
