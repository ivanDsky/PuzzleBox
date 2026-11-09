package com.ivandsky.puzzlebox.games.tango.editor

import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.game.VariantId
import com.ivandsky.puzzlebox.game.editor.Budget
import com.ivandsky.puzzlebox.game.editor.DraftExport
import com.ivandsky.puzzlebox.game.editor.DraftState
import com.ivandsky.puzzlebox.game.editor.EditorNotation
import com.ivandsky.puzzlebox.game.editor.EditorShareCodec
import com.ivandsky.puzzlebox.game.editor.GameEditor
import com.ivandsky.puzzlebox.game.editor.PublishBlock
import com.ivandsky.puzzlebox.game.editor.Solution
import com.ivandsky.puzzlebox.game.editor.Verdict
import com.ivandsky.puzzlebox.games.tango.TangoDefinition
import com.ivandsky.puzzlebox.games.tango.TangoPuzzle
import com.ivandsky.puzzlebox.games.tango.codec.TangoCodec
import com.ivandsky.puzzlebox.model.GameId

/**
 * Tango's pure editor (ADR-0019): one variant per offered [TangoDraft.SIZES] width, a new board
 * has no givens and no markers, drafts persist through [TangoDraftCodec], analysis is
 * [TangoDraftAnalyzer] and publishing is gated by [TangoPublishGates]. A unique draft exports
 * through [TangoCodec] so test solve
 * plays a real puzzle, and shares as givens-and-markers [TangoShareCodec] bytes. Its text form
 * is [TangoNotation] (M5-12-a).
 */
object TangoEditor :
    GameEditor<TangoDraft>,
    DraftExport<TangoDraft>,
    EditorShareCodec<TangoDraft> by TangoShareCodec,
    EditorNotation<TangoDraft> by TangoNotation {
    override val gameId: GameId get() = TangoDefinition.id

    override val variants: List<VariantId> = listOf(TangoDraft.variantOf(6))

    override val draftFormatVersion: Int get() = TangoDraftCodec.VERSION

    override fun newDraft(variant: VariantId): TangoDraft {
        val n = TangoDraft.sizeOf(variant)
        require(n != null && variant in variants) { "unknown Tango variant $variant" }
        return TangoDraft(n, emptyMap(), emptyList())
    }

    override fun variantOf(draft: TangoDraft): VariantId = TangoDraft.variantOf(draft.n)

    override fun encodeDraft(state: DraftState<TangoDraft>): ByteArray = TangoDraftCodec.encode(state)

    override fun decodeDraft(bytes: ByteArray): DecodeResult<DraftState<TangoDraft>> = TangoDraftCodec.decode(bytes)

    override fun analyze(draft: TangoDraft, budget: Budget): Verdict = TangoDraftAnalyzer.analyze(draft, budget)

    override fun publishBlocks(draft: TangoDraft, verdict: Verdict): List<PublishBlock> =
        TangoPublishGates.blocks(draft, verdict)

    /** TangoCodec bytes of the givens and markers; the solution is re-derived on load, not stored. */
    override fun toPuzzle(draft: TangoDraft, solution: Solution): Puzzle {
        val variant = variantOf(draft)
        val puzzle = TangoPuzzle(variant, draft.n, draft.givens, draft.markers)
        return Puzzle(
            gameId = gameId,
            variant = variant,
            formatVersion = TangoCodec.VERSION,
            payload = TangoCodec.encode(puzzle),
        )
    }
}
