package com.ivandsky.puzzlebox.games.zip.editor

import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.VariantId
import com.ivandsky.puzzlebox.game.editor.Budget
import com.ivandsky.puzzlebox.game.editor.DraftResize
import com.ivandsky.puzzlebox.game.editor.DraftState
import com.ivandsky.puzzlebox.game.editor.GameEditor
import com.ivandsky.puzzlebox.game.editor.Verdict
import com.ivandsky.puzzlebox.games.zip.Cell
import com.ivandsky.puzzlebox.games.zip.Wall
import com.ivandsky.puzzlebox.games.zip.WallDirection
import com.ivandsky.puzzlebox.games.zip.ZipDefinition
import com.ivandsky.puzzlebox.games.zip.engine.ZipVariants
import com.ivandsky.puzzlebox.model.GameId

/**
 * Zip's pure editor (ADR-0019): a new board is the variant's size with no walls, no path and
 * no waypoints, so it starts Incomplete; the variant table is the daily sizes n5..n8. Resizing
 * keeps the top-left: the path is cut at its first cell outside, waypoints and walls outside
 * are dropped, and the path's ends are numbered again.
 */
object ZipEditor : GameEditor<ZipDraft>, DraftResize<ZipDraft> {
    override val gameId: GameId get() = ZipDefinition.id

    override val variants: List<VariantId> = ZipVariants.all.map { it.id }

    override val draftFormatVersion: Int get() = ZipDraftCodec.VERSION

    override fun newDraft(variant: VariantId): ZipDraft {
        val size = requireNotNull(ZipVariants.byId(variant)) { "unknown Zip variant $variant" }.size
        return ZipDraft(size, emptySet(), emptyList(), emptyList())
    }

    override fun variantOf(draft: ZipDraft): VariantId = VariantId("zip/n${draft.n}")

    override fun encodeDraft(state: DraftState<ZipDraft>): ByteArray = ZipDraftCodec.encode(state)

    override fun decodeDraft(bytes: ByteArray): DecodeResult<DraftState<ZipDraft>> = ZipDraftCodec.decode(bytes)

    override fun analyze(draft: ZipDraft, budget: Budget): Verdict = ZipDraftAnalyzer.analyze(draft, budget)

    override fun resized(draft: ZipDraft, variant: VariantId): ZipDraft {
        val size = requireNotNull(ZipVariants.byId(variant)) { "unknown Zip variant $variant" }.size
        val old = draft.n
        fun fits(cell: Int) = cell / old < size && cell % old < size
        fun moved(cell: Int) = cell / old * size + cell % old
        val walls = draft.walls.filter { wall ->
            val cell = wall.cell.idx
            val (row, col) = cell / old to cell % old
            when (wall.direction) {
                WallDirection.RIGHT -> row < size && col < size - 1
                WallDirection.DOWN -> row < size - 1 && col < size
            }
        }.map { Wall(Cell(moved(it.cell.idx)), it.direction) }.toSet()
        val path = draft.path.takeWhile(::fits).map(::moved)
        val kept = ZipDraft(size, walls, path, draft.waypoints.filter(::fits).map(::moved))
        return kept.stroke(path).apply(kept)
    }
}
