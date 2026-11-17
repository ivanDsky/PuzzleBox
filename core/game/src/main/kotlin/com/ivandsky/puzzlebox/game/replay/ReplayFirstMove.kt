package com.ivandsky.puzzlebox.game.replay

/**
 * Whether a replay asks its game's hint engine with the first-move option (nav-rethink B4) when it
 * recomputes the cell a recorded hint pointed at. The stored replay cannot carry the option (ADR-0025),
 * so a replay opened after a flip asks with the value of the day. Read at each mark, never cached.
 *
 * A game's UI module binds one under the game's id and answers from the very source its play UI takes its
 * hint mode from, so no engine reads a release flag (A2). A graph that binds none replays in the legacy order.
 */
fun interface ReplayFirstMove {
    fun enabled(): Boolean

    companion object {
        /** The legacy order: a replay marks the cell the ladder order picks. */
        val Off: ReplayFirstMove = ReplayFirstMove { false }
    }
}
