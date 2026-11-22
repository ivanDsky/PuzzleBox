package com.ivandsky.puzzlebox.games.tangoui.trainer

import com.ivandsky.puzzlebox.games.tango.TangoMarkerKind
import com.ivandsky.puzzlebox.games.tango.engine.TangoVerdict
import com.ivandsky.puzzlebox.games.tango.render.TangoViolations.Violation

/**
 * The line a practice board shows for a move that breaks a rule outright. The move is undone before the band shows, so
 * the daily's own phrases ("These two must be the same.", "Three suns in a row.", "Row 2 has 4 suns") would point at
 * squares that are gone. These say what the move would have done, in the game's own words.
 */
internal object TangoWrongMoveCopy {

    private val overFull = Regex("""^(Row|Column) (\d+) has (\d+) (suns|moons) — a (row|column) holds (\d+)\.$""")

    /** The line for the move at [idx] given the [violations] it made, or null when it broke none. */
    fun why(violations: List<Violation>, idx: Int): String? {
        val violation = violations.firstOrNull { idx in it.cells } ?: violations.firstOrNull() ?: return null
        return when (violation.reason) {
            TangoVerdict.Reason.MarkerConflict -> violation.marker?.let { "That breaks the ${sign(it.kind)} sign beside it." }
            TangoVerdict.Reason.ThreeInARow -> "That would make ${violation.phrase.replaceFirstChar(Char::lowercase)}"
            TangoVerdict.Reason.CountExceeded -> overFull.matchEntire(violation.phrase)?.destructured?.let { (line, at, count, kind, axis, half) ->
                "That would put $count $kind in ${line.lowercase()} $at, and a $axis holds $half."
            }
            else -> null
        } ?: violation.phrase
    }

    private fun sign(kind: TangoMarkerKind): String = if (kind == TangoMarkerKind.SAME) "=" else "×"
}
