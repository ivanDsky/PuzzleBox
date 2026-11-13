package com.ivandsky.puzzlebox.game.replay

/** How the timeline and the board mark one board-changing move. */
class MoveMark(val kind: MoveKind, val hinted: Set<Int>) {
    companion object {
        val Plain = MoveMark(MoveKind.Normal, emptySet())
    }
}
