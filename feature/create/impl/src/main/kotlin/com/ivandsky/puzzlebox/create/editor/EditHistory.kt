package com.ivandsky.puzzlebox.create.editor

import com.ivandsky.puzzlebox.game.editor.EditorDraft
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The open draft's undo and redo (spec S3): it holds the current [EditorDraft] and walks the
 * draft's own command history one step at a time, so no screen keeps a second stack.
 * [canUndo] and [canRedo] follow the current draft; both are false until [open].
 */
internal class EditHistory {

    private var current: EditorDraft<*>? = null

    private val undoable = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = undoable.asStateFlow()

    private val redoable = MutableStateFlow(false)
    val canRedo: StateFlow<Boolean> = redoable.asStateFlow()

    /** The loaded draft, with whatever history it was stored with. */
    fun open(draft: EditorDraft<*>) = moveTo(draft)

    /** An edit the game's controller has already applied to the current draft. */
    fun record(next: EditorDraft<*>) = moveTo(next)

    /** One step back; null when there is nothing to undo. */
    fun undo(): EditorDraft<*>? = current?.undo()?.also(::moveTo)

    /** The undone step again; null when there is nothing to redo. */
    fun redo(): EditorDraft<*>? = current?.redo()?.also(::moveTo)

    private fun moveTo(draft: EditorDraft<*>) {
        current = draft
        undoable.value = draft.canUndo
        redoable.value = draft.canRedo
    }
}
