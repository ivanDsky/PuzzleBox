package com.ivandsky.puzzlebox.games.tango.editor

import com.ivandsky.puzzlebox.games.tango.TangoCell
import com.ivandsky.puzzlebox.games.tango.TangoMarker

/**
 * An editable Tango board, always [N] × [N]: [givens] keyed by row-major cell index, and
 * [markers] on interior edges. Partial and contradictory boards are valid drafts; only
 * analysis judges them.
 */
data class TangoDraft(val givens: Map<Int, TangoCell>, val markers: List<TangoMarker>) {
    companion object { const val N: Int = 6 }
}
