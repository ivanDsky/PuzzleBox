package com.ivandsky.puzzlebox.games.strands.feed.gate

import java.time.LocalDate

/** One A–Z letter per cell; any other letter would break the codec's letter alphabet and the spelling check. */
object LettersCheck : StrandsTrustCheck {
    override fun problem(wire: CompleteWire, requested: LocalDate): String? =
        if (wire.board.all { row -> row.all { it in 'A'..'Z' } }) null else "board cell is not A-Z"
}
