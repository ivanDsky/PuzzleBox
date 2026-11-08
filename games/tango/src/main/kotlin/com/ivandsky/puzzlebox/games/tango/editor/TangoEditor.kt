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
 * Tango's pure editor (ADR-0019): one 6×6 variant, a new board has no givens and no markers,
 * drafts persist through [TangoDraftCodec], analysis is [TangoDraftAnalyzer] and publishing
 * is gated by [TangoPublishGates]. A unique draft exports through [TangoCodec] so test solve
 * plays a real puzzle, and shares as givens-and-markers [TangoShareCodec] bytes. Its text form
 * is [TangoNotation] (M5-12-a).
 */
object TangoEditor :
    GameEditor<TangoDraft>,
    DraftExport<TangoDraft>,
    EditorShareCodec<TangoDraft> by TangoShareCodec,
    EditorNotation<TangoDraft> by TangoNotation {
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

    override fun publishBlocks(draft: TangoDraft, verdict: Verdict): List<PublishBlock> =
        TangoPublishGates.blocks(draft, verdict)

    /** TangoCodec bytes of the givens and markers; the solution is re-derived on load, not stored. */
    override fun toPuzzle(draft: TangoDraft, solution: Solution): Puzzle {
        val puzzle = TangoPuzzle(N6, TangoDraft.N, draft.givens, draft.markers)
        return Puzzle(
            gameId = gameId,
            variant = N6,
            formatVersion = TangoCodec.VERSION,
            payload = TangoCodec.encode(puzzle),
        )
    }
}
