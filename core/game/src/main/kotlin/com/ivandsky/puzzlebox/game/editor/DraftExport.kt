package com.ivandsky.puzzlebox.game.editor

import com.ivandsky.puzzlebox.game.Puzzle

/** Capability: the draft can become a playable Puzzle (test solve now, publish in M3). */
interface DraftExport<D : Any> {
    /** Canonical Puzzle through the game's existing codec, with [solution] embedded. */
    fun toPuzzle(draft: D, solution: Solution): Puzzle
}
