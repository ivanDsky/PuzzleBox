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
    val next = state.accept(verdict)
    return if (next == state) this else copy(state = next)
}

/** Puts the revealed hint on the board and counts it. */
fun StrandsSession.withHint(hint: StrandsHint.Reveal): StrandsSession = copy(state = state.spend(hint))
