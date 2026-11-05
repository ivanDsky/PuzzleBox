package com.ivandsky.puzzlebox.games.strands.feed

import com.ivandsky.puzzlebox.games.strands.feed.gate.FieldsCheck
import com.ivandsky.puzzlebox.games.strands.feed.gate.LettersCheck
import com.ivandsky.puzzlebox.games.strands.feed.gate.PrintDateCheck
import com.ivandsky.puzzlebox.games.strands.feed.gate.RectangularCheck
import com.ivandsky.puzzlebox.games.strands.feed.gate.SpangramEdgesCheck
import com.ivandsky.puzzlebox.games.strands.feed.gate.StrandsTrustCheck
import com.ivandsky.puzzlebox.games.strands.feed.gate.TilingCheck
import com.ivandsky.puzzlebox.games.strands.feed.gate.WordPathsCheck
import java.time.LocalDate

/**
 * Decides whether a parsed day may be played: null when trusted, else the first failing
 * check's reason. A trusted wire has every field present and one in-board `[row, col]` per
 * letter of every word, so building the puzzle from it cannot fail.
 */
object StrandsTrustGate {

    /** Ordered: each check may rely on the ones before it. */
    private val checks: List<StrandsTrustCheck> = listOf(
        PrintDateCheck,
        RectangularCheck,
        LettersCheck,
        WordPathsCheck,
        TilingCheck,
        SpangramEdgesCheck,
    )

    fun check(wire: StrandsWire, requested: LocalDate): String? {
        val complete = when (val fields = FieldsCheck.check(wire)) {
            is FieldsCheck.Outcome.Missing -> return "missing ${fields.field}"
            is FieldsCheck.Outcome.Complete -> fields.wire
        }
        return checks.firstNotNullOfOrNull { it.problem(complete, requested) }
    }
}
