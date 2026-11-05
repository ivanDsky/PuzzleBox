package com.ivandsky.puzzlebox.games.strands.feed.gate

import java.time.LocalDate

/** The body is the requested day, not a neighbour the server or a cache handed back. */
object PrintDateCheck : StrandsTrustCheck {
    override fun problem(wire: CompleteWire, requested: LocalDate): String? =
        if (wire.printDate == requested.toString()) null else "printDate ${wire.printDate} is not $requested"
}
