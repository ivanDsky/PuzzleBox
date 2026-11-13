package com.ivandsky.puzzlebox.game.editor

import com.ivandsky.puzzlebox.game.Command
import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.game.VariantId
import com.ivandsky.puzzlebox.game.map
import com.ivandsky.puzzlebox.model.GameId
import java.security.MessageDigest

/**
 * The type-erased draft handle features hold (ADR-0019): features keep an `EditorDraft<*>`,
 * and every member they call is free of [D]. Edits run through the shared [CommandHistory],
 * so no game re-implements undo/redo. Immutable: every edit returns a new handle.
 */
class EditorDraft<D : Any>(val editor: GameEditor<D>, val state: DraftState<D>) {
    val gameId: GameId get() = editor.gameId
    val variant: VariantId get() = editor.variantOf(state.draft)
    val canUndo: Boolean get() = state.history.canUndo
    val canRedo: Boolean get() = state.history.canRedo

    /** True when [editor] also implements [DraftExport]. */
    val canTestSolve: Boolean get() = editor is DraftExport<*>

    /** The board equals the editor's blank board of this variant; history is ignored. */
    val isBlank: Boolean get() = state.draft == editor.newDraft(variant)

    /** SHA-256 hex of editor.encodeDraft(DraftState(editor.layoutOf(state.draft))): layout only, empty history. */
    val layoutKey: String by lazy {
        MessageDigest.getInstance("SHA-256")
            .digest(editor.encodeDraft(DraftState(editor.layoutOf(state.draft))))
            .joinToString("") { "%02x".format(it) }
    }

    /** Applies [command] to the board and records it as one undoable step. */
    fun apply(command: Command<D>): EditorDraft<D> =
        EditorDraft(editor, DraftState(command.apply(state.draft), state.history.record(command)))

    /** The previous step; null when there is nothing to undo. */
    fun undo(): EditorDraft<D>? =
        state.history.undo(state.draft)?.let { EditorDraft(editor, DraftState(it.state, it.history)) }

    /** The undone step re-applied; null when there is nothing to redo. */
    fun redo(): EditorDraft<D>? =
        state.history.redo(state.draft)?.let { EditorDraft(editor, DraftState(it.state, it.history)) }

    /**
     * One undoable step: the editor's [DraftResize] board when it has one, else a blank
     * [variant] board.
     */
    fun resize(variant: VariantId): EditorDraft<D> = apply(
        ReplaceDraft(state.draft, editor.capability<D, DraftResize<D>>()?.resized(state.draft, variant) ?: editor.newDraft(variant)),
    )

    /** One undoable step: always a blank board of the current variant. */
    fun clear(): EditorDraft<D> = apply(ReplaceDraft(state.draft, editor.newDraft(variant)))

    fun encode(): ByteArray = editor.encodeDraft(state)

    fun analyze(budget: Budget): Verdict = editor.analyze(state.draft, budget)

    /** The game's board gates for this board under [verdict]; empty for a game without any. */
    fun publishBlocks(verdict: Verdict): List<PublishBlock> = editor.publishBlocks(state.draft, verdict)

    /** The editor's [DraftExport] of this board; null when !canTestSolve. */
    fun toPuzzle(solution: Solution): Puzzle? =
        editor.capability<D, DraftExport<D>>()?.toPuzzle(state.draft, solution)

    companion object {
        /** A blank [variant] board with an empty history. */
        fun <D : Any> fresh(editor: GameEditor<D>, variant: VariantId): EditorDraft<D> =
            EditorDraft(editor, DraftState(editor.newDraft(variant)))

        /** Reopens a stored draft blob; the editor's codec owns every rejection. */
        fun <D : Any> open(editor: GameEditor<D>, bytes: ByteArray): DecodeResult<EditorDraft<D>> =
            editor.decodeDraft(bytes).map { EditorDraft(editor, it) }
    }
}
