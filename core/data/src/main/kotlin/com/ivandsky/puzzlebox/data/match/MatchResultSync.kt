package com.ivandsky.puzzlebox.data.match

/** Retry pending result posts now (duel screen open). Clears the in-process tried set; never throws. */
fun interface MatchResultSync { fun poke() }
