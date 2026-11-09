package com.ivandsky.puzzlebox.create.editor

import com.ivandsky.puzzlebox.game.VariantId
import com.ivandsky.puzzlebox.game.editor.EditorDraft
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The open draft's undo and redo (spec S3): it holds the current [EditorDraft] and walks the
 * draft's own command history one step at a time, so no screen keeps a second stack. Resize
 * and clear (A5) are steps on that same history. Every move hands its board to [adopt]; while
 * [locked] (a published draft) nothing moves. [canUndo] and [canRedo] follow the current
 * draft; both are false until [open].
 */
internal class EditHistory(private val locked: () -> Boolean, private val adopt: (EditorDraft<*>) -> Unit) {

    private var current: EditorDraft<*>? = null

    private val undoable = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = undoable.asStateFlow()

    private val redoable = MutableStateFlow(false)
    val canRedo: StateFlow<Boolean> = redoable.asStateFlow()

    /** The loaded draft, with whatever history it was stored with. */
    fun open(draft: EditorDraft<*>) = moveTo(draft)

    /** An edit the game's controller has already applied to the current draft. */
    fun record(next: EditorDraft<*>) = moveTo(next)

    /** One step back; nothing moves when there is nothing to undo. */
    fun undo() = move { it.undo() }

    /** The undone step again; nothing moves when there is nothing to redo. */
    fun redo() = move { it.redo() }

    /**
     * Back to the nearest earlier step [target] accepts, however many steps that takes; nothing
     * moves when no earlier step does.
     */
    fun undoTo(target: (EditorDraft<*>) -> Boolean) = move { draft ->
        var step = draft.undo()
        while (step != null && !target(step)) step = step.undo()
        step
    }

    /** A [variant] board as one step ([EditorDraft.resize]); nothing moves when the draft already has that size. */
    fun resize(variant: VariantId) = move { draft -> draft.takeIf { it.variant != variant }?.resize(variant) }

    /** A blank board of the same size as one step. */
    fun clear() = move { it.clear() }

    /** The board [step] makes of the current draft becomes current and goes to [adopt]; none before [open]. */
    private fun move(step: (EditorDraft<*>) -> EditorDraft<*>?) {
        if (locked()) return
        current?.let(step)?.let { next ->
            moveTo(next)
            adopt(next)
        }
    }

    private fun moveTo(draft: EditorDraft<*>) {
        current = draft
        undoable.value = draft.canUndo
        redoable.value = draft.canRedo
    }
}
