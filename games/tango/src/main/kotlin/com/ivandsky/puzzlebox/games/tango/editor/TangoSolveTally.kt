package com.ivandsky.puzzlebox.games.tango.editor

/**
 * What one [TangoSolveWalk] of a board took: how many cells each rung of the hint ladder placed
 * ([marker], [triple], [lineFull], [lineFills]), how many of those steps were [narrow] (one move
 * on offer and more than one cell still empty), and how many cells were [left] empty when no rung
 * had a move (0: the ladder finished the board).
 */
internal data class TangoSolveTally(
    val marker: Int,
    val triple: Int,
    val lineFull: Int,
    val lineFills: Int,
    val narrow: Int,
    val left: Int,
) {
    /** The hardest rung the walk used, as [TangoGrader] names it; null when it placed nothing. */
    val hardest: TangoRung?
        get() = when {
            lineFills > 0 -> TangoRung.LINE_FILLS
            lineFull > 0 -> TangoRung.LINE_FULL
            triple > 0 -> TangoRung.THREE_IN_A_ROW
            marker > 0 -> TangoRung.MARKER_BINDING
            else -> null
        }
}
