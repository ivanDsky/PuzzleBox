package com.ivandsky.puzzlebox.games.strands.feed.gate

import java.time.LocalDate

/**
 * One rule of the Strands trust gate, read after [FieldsCheck] has proved every field present.
 * Null when [wire] passes, else the reason it does not. Checks run in order, so each may rely on
 * the ones before it (a word-path check sees a rectangular A–Z board).
 */
fun interface StrandsTrustCheck {
    fun problem(wire: CompleteWire, requested: LocalDate): String?
}
