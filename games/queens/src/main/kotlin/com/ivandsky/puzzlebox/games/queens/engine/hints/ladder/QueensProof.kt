package com.ivandsky.puzzlebox.games.queens.engine.hints.ladder

/** A [deduction] and the cells its rung read to prove it; the rung states this itself, nothing rebuilds it. */
data class QueensProof(val deduction: QueensDeduction, val evidence: Set<Int>)
