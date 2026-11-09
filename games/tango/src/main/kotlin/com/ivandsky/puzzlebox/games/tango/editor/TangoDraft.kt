package com.ivandsky.puzzlebox.games.tango.editor

import com.ivandsky.puzzlebox.game.VariantId
import com.ivandsky.puzzlebox.games.tango.TangoCell
import com.ivandsky.puzzlebox.games.tango.TangoMarker

/**
 * An editable Tango board, [n] × [n] with n ∈ [SIZES]: [givens] keyed by row-major cell
 * index, and [markers] on interior edges. Partial and contradictory boards are valid drafts;
 * only analysis judges them.
 */
data class TangoDraft(val n: Int, val givens: Map<Int, TangoCell>, val markers: List<TangoMarker>) {
    init { require(n in SIZES) { "Tango board is $n wide; sizes are $SIZES" } }

    companion object {
        /** The board widths the editor can build. */
        val SIZES: List<Int> = listOf(4, 6, 8, 10)

        fun variantOf(n: Int): VariantId = VariantId("tango/n$n")

        /** The width of [variant], or null when it is not a Tango editor size. */
        fun sizeOf(variant: VariantId): Int? = SIZES.firstOrNull { variantOf(it) == variant }
    }
}
