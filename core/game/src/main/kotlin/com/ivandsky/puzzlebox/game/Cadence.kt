package com.ivandsky.puzzlebox.game

import java.time.LocalDate
import java.time.ZoneId

/** When a game serves a fresh puzzle. */
sealed interface Cadence {
    /** A new puzzle at midnight in [zone]. Every game uses America/Los_Angeles (research 01, A11)
     *  so all days roll over together regardless of the device zone. */
    data class Daily(val zone: ZoneId) : Cadence
}

/** The cadence's current day — the date a fresh fetch is keyed under. */
fun Cadence.today(): LocalDate = when (this) {
    is Cadence.Daily -> LocalDate.now(zone)
}
