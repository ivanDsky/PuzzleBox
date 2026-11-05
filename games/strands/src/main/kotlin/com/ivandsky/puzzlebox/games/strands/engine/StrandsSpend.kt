package com.ivandsky.puzzlebox.games.strands.engine

import com.ivandsky.puzzlebox.games.strands.StrandsState

/** Puts [hint] on the board and counts it — the only place [StrandsState.hintsUsed] grows. */
fun StrandsState.spend(hint: StrandsHint.Reveal): StrandsState =
    copy(activeHint = hint.hint, hintsUsed = hintsUsed + 1)
