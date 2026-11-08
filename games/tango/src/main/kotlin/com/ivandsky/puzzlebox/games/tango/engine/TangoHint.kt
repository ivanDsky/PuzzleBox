package com.ivandsky.puzzlebox.games.tango.engine

import com.ivandsky.puzzlebox.game.Hint
import com.ivandsky.puzzlebox.games.tango.TangoCell
import com.ivandsky.puzzlebox.games.tango.TangoMarkerKind

/**
 * Tango hint (F3 marker): a forced move plus WHY it is forced (spec hints bullet — the hint
 * reveals the rule and evidence, never just a cell). Produced by the deduction ladder in
 * [TangoRules] (marker → triple → count → line completion); usage is counted by the caller
 * (A3).
 */
sealed interface TangoHint : Hint {

    /** Cell [idx] must be [value]; [why] names the rule and the evidence that forces it. */
    data class Forced(val idx: Int, val value: TangoCell, val why: Why) : TangoHint

    /** No forced move exists on the current grid. */
    data object Stuck : TangoHint

    /** The rule plus evidence behind a [Forced] hint. */
    sealed interface Why {
        /** The `=`/`×` edge between [markerCell] and [neighborIdx] propagates the filled end's value. */
        data class MarkerBinding(val markerCell: Int, val neighborIdx: Int, val kind: TangoMarkerKind) : Why

        /** [first] and [second] are identical and adjacent; the hinted cell would make three in a row. */
        data class ThreeInARow(val first: Int, val second: Int) : Why

        /**
         * The line (row [row] when non-null, else column [col]) already holds n/2 of [fullOf];
         * the hinted cell must take the other value.
         */
        data class LineFull(val row: Int?, val col: Int?, val fullOf: TangoCell) : Why

        /**
         * The line (row [row] when non-null, else column [col]) has [fills] legal completions
         * and every one of them gives the hinted cell the same value.
         */
        data class LineFills(val row: Int?, val col: Int?, val fills: Int) : Why
    }
}
