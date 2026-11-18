package com.ivandsky.puzzlebox.games.strands

import com.ivandsky.puzzlebox.games.strands.engine.StrandsHint
import com.ivandsky.puzzlebox.games.strands.engine.StrandsMove
import com.ivandsky.puzzlebox.games.strands.engine.StrandsRules
import com.ivandsky.puzzlebox.games.strands.engine.StrandsVerdict
import com.ivandsky.puzzlebox.games.strands.engine.accept
import com.ivandsky.puzzlebox.games.strands.engine.spend

/** Validates and applies a submitted path. Same instance back when nothing changed. */
fun StrandsSession.withSubmit(path: List<Coord>): StrandsSession {
    val verdict = StrandsRules.validate(StrandsMove(path), state) as StrandsVerdict
    val next = state.accept(verdict, path)
    return if (next == state) this else copy(state = next)
}

/**
 * The session with the traces the player drew for its found words, read back from [submits] (the logged
 * submitted paths, oldest first): a saved session stores found words as text, the play's move log keeps the drawing.
 */
fun StrandsSession.withKeptPaths(submits: List<List<Coord>>): StrandsSession {
    val blank = StrandsSession(ref = null, state = StrandsState(state.puzzle))
    val kept = submits.fold(blank) { session, path -> session.withSubmit(path) }.state.foundPaths.filterKeys { it in state.found }
    return if (kept.isEmpty()) this else copy(state = state.copy(foundPaths = kept))
}

/** Puts the revealed hint on the board and counts it. */
fun StrandsSession.withHint(hint: StrandsHint.Reveal): StrandsSession = copy(state = state.spend(hint))
