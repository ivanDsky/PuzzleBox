package com.ivandsky.puzzlebox.gameui.board

import com.ivandsky.puzzlebox.game.RankOrder

/**
 * The viewer's row as the dock draws it while the board is between two (see [BoardMorph]): [self] as the board it goes
 * to ranks it ([order], [withPuzzleBoxRank]), changing from the cells it [was] on the board it leaves as the row in the
 * list does (see [RowScrub]). A row only the board being left has [leaves]: it is that board's, and frosts out.
 */
internal class DockScrub(
    val self: DailyBoardLine.Real,
    val order: RankOrder,
    val withPuzzleBoxRank: Boolean,
    val was: RowScrub?,
    val leaves: Boolean,
    val progress: () -> Float,
)

// The viewer's own row among a board's parts: the pinned one under the divider, or the one among the rows.
private fun List<BoardPart>.selfRow(): BoardPart.Row? {
    val divider = indexOf(BoardPart.Divider)
    if (divider >= 0) return getOrNull(divider + 1) as? BoardPart.Row
    return firstNotNullOfOrNull { part -> (part as? BoardPart.Row)?.takeIf { (it.line as? DailyBoardLine.Real)?.row?.isSelf == true } }
}

/** The dock between these two boards; none when the board being left has no row of the viewer's to dock. */
internal fun MorphParts.dock(medals: Boolean): DockScrub? {
    val old = from.selfRow() ?: return null
    val row = to.selfRow() ?: old
    val self = row.line as? DailyBoardLine.Real ?: return null
    val was = if (row === old) null else RowScrub(old.line.cells(old.order, old.withPuzzleBoxRank, medals), progress)
    return DockScrub(self, row.order, row.withPuzzleBoxRank, was, leaves = row === old, progress)
}

// The docked row's own clock: a new place rolls and a new time counts at once, with no stagger and no medal flip.
internal object DockPlaces : PlaceMotion {
    override val animated = true
    override val delayMs = 0
    override val arrived = false
}
