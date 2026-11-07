package com.ivandsky.puzzlebox.games.queens.editor

import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.game.VariantId
import com.ivandsky.puzzlebox.game.editor.Budget
import com.ivandsky.puzzlebox.game.editor.DraftExport
import com.ivandsky.puzzlebox.game.editor.DraftState
import com.ivandsky.puzzlebox.game.editor.EditorShareCodec
import com.ivandsky.puzzlebox.game.editor.GameEditor
import com.ivandsky.puzzlebox.game.editor.PublishBlock
import com.ivandsky.puzzlebox.game.editor.Solution
import com.ivandsky.puzzlebox.game.editor.Verdict
import com.ivandsky.puzzlebox.games.queens.Cell
import com.ivandsky.puzzlebox.games.queens.QueensDefinition
import com.ivandsky.puzzlebox.games.queens.QueensPuzzle
import com.ivandsky.puzzlebox.games.queens.QueensVariants
import com.ivandsky.puzzlebox.games.queens.codec.QueensCodec
import com.ivandsky.puzzlebox.games.queens.queensVariantId
import com.ivandsky.puzzlebox.model.GameId

/**
 * Queens' pure editor (ADR-0019): a new board, also what resize and clear reset to (A5), has
 * every cell [QueensDraft.UNPAINTED] (ledger ruling: blank = empty), so it starts Incomplete
 * on the unpainted count; the variant table is the shipped sizes 4..12 (A5). A unique draft
 * exports through the daily [QueensCodec] (A10), so test solve plays a real puzzle, and it
 * shares as board-only [QueensShareCodec] bytes the recipient re-solves.
 */
object QueensEditor :
    GameEditor<QueensDraft>,
    DraftExport<QueensDraft>,
    EditorShareCodec<QueensDraft> by QueensShareCodec {
    override val gameId: GameId get() = QueensDefinition.id

    override val variants: List<VariantId> = QueensVariants.all.map { it.id }

    override val draftFormatVersion: Int get() = QueensDraftCodec.VERSION

    override fun newDraft(variant: VariantId): QueensDraft {
        val size = requireNotNull(QueensVariants.byId(variant)) { "unknown Queens variant $variant" }.size
        return QueensDraft(size, List(size * size) { QueensDraft.UNPAINTED })
    }

    override fun variantOf(draft: QueensDraft): VariantId = queensVariantId(draft.size)

    override fun encodeDraft(state: DraftState<QueensDraft>): ByteArray = QueensDraftCodec.encode(state)

    override fun decodeDraft(bytes: ByteArray): DecodeResult<DraftState<QueensDraft>> = QueensDraftCodec.decode(bytes)

    override fun analyze(draft: QueensDraft, budget: Budget): Verdict = QueensDraftAnalyzer.analyze(draft, budget)

    override fun publishBlocks(draft: QueensDraft, verdict: Verdict): List<PublishBlock> =
        QueensPublishGates.blocks(draft, verdict)

    /** Answer crowns are author-only: placing, moving or removing one never clears a test solve. */
    override fun layoutOf(draft: QueensDraft): QueensDraft = draft.copy(crowns = emptySet())

    /** QueensCodec bytes of the fully painted regions with [solution]'s queen cells; crowns never reach it (A23). */
    override fun toPuzzle(draft: QueensDraft, solution: Solution): Puzzle {
        val puzzle = QueensPuzzle(draft.size, draft.regions, solution = solution.values.map(::Cell))
        return Puzzle(
            gameId = gameId,
            variant = variantOf(draft),
            formatVersion = QueensCodec.VERSION,
            payload = QueensCodec.encode(puzzle),
        )
    }
}
