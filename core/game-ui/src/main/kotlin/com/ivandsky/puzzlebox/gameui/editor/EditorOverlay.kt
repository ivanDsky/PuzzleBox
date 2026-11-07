package com.ivandsky.puzzlebox.gameui.editor

/**
 * The verdict card's witness choice on a 2+ solutions verdict (spec A7): which of the two
 * solutions ([Witness.A] = `Verdict.Multiple.solutionA`) the card describes and, when
 * [showOnBoard], that the board draws the selected witness's queens (pieces) on the board.
 */
data class EditorOverlay(val witness: Witness = Witness.A, val showOnBoard: Boolean = false) {
    enum class Witness { A, B }
}
