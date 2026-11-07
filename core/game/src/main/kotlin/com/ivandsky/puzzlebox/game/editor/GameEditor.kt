package com.ivandsky.puzzlebox.game.editor

import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.VariantId
import com.ivandsky.puzzlebox.model.GameId

/**
 * One game's pure editor (ADR-0019, spec A4): the board model [D] a creator edits, its
 * variant table, its versioned draft codec and its analysis. No Compose, no Android; the
 * game's UI half is [com.ivandsky.puzzlebox.gameui.editor.GameEditorUi]. Features never see
 * [D]: they hold an [EditorDraft] star projection.
 */
interface GameEditor<D : Any> {
    val gameId: GameId

    /** Variant table only; the size picker never offers anything else (A5). */
    val variants: List<VariantId>
    val draftFormatVersion: Int

    /** Blank board; also what resize and clear reset to (A5). */
    fun newDraft(variant: VariantId): D
    fun variantOf(draft: D): VariantId

    /** Draft codec: board + bounded undo/redo history; partial boards allowed. [D] holds board state only. */
    fun encodeDraft(state: DraftState<D>): ByteArray
    fun decodeDraft(bytes: ByteArray): DecodeResult<DraftState<D>>

    /** Pure and blocking. Polls [budget] in the search loop; exhausted ⇒ Verdict.Undetermined. */
    fun analyze(draft: D, budget: Budget): Verdict

    /** Board gates outside the solver (contiguity in 09, needs-guessing in 11). */
    fun publishBlocks(draft: D, verdict: Verdict): List<PublishBlock> = emptyList()

    /**
     * The part of [draft] that decides layout identity ([EditorDraft.layoutKey]). Author-only aids
     * that never reach the puzzle (Queens answer crowns) are stripped here, so editing them never
     * clears a test solve. The default is the draft itself.
     */
    fun layoutOf(draft: D): D = draft
}
