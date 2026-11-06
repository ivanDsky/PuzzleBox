package com.ivandsky.puzzlebox.game.editor

import com.ivandsky.puzzlebox.game.Command

/** Whole-board replacement as one undoable step (resize, clear). */
data class ReplaceDraft<D : Any>(val before: D, val after: D) : Command<D> {
    override fun apply(state: D): D = after
    override fun revert(state: D): D = before
}
