package com.ivandsky.puzzlebox.games.strands.render

/**
 * A cell's base look (A40, extension point a). Precedence when several apply:
 * [FoundSpangram] > [FoundTheme] > [HintOrderRevealed] > [HintOutlined] > [Idle]. The live
 * trace is not a state: it overlays any of these through [StrandsCell.traceIndex].
 */
sealed interface CellState {
    data object Idle : CellState
    data object FoundTheme : CellState
    data object FoundSpangram : CellState
    data object HintOutlined : CellState

    /** [index] is the cell's position in the hinted word's spelling. */
    data class HintOrderRevealed(val index: Int) : CellState
}
