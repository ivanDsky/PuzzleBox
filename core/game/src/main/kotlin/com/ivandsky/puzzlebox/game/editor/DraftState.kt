package com.ivandsky.puzzlebox.game.editor

import com.ivandsky.puzzlebox.game.Command
import com.ivandsky.puzzlebox.game.CommandHistory

/** A draft board plus its undo/redo history: what the draft codec persists. */
data class DraftState<D : Any>(
    val draft: D,
    val history: CommandHistory<D, Command<D>> = CommandHistory(),
)
