package com.ivandsky.puzzlebox.gameui.board

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import com.ivandsky.puzzlebox.game.RankOrder

/**
 * The cells of a row that differ from board to board, while the row stays the same person: its [place] (null while
 * hidden, "100+" when [beyondPage]), the small [detail] and [mark] chips, the [score], whether its place makes it a
 * medal row ([medalRow]) and whether its place cell may wear a disc ([placeMedal]; an unofficial row takes no place).
 */
internal data class RowCells(
    val place: Int?,
    val detail: String?,
    val score: String,
    val mark: String? = null,
    val beyondPage: Boolean = false,
    val medalRow: Boolean = false,
    val placeMedal: Boolean = true,
) {
    /** The medal row look at the place the row is at, if it wears one. */
    val medal: MedalRow? get() = if (medalRow) medalRowOf(place) else null

    /** Whether the place is a number a roll can pass. */
    val counted: Boolean get() = place != null && !beyondPage
}

/** The row's minimum height: the winner's is a little taller. */
internal val RowCells.minHeight: Dp get() = medal?.minHeight ?: BoardRowMinHeight

/** The cells of this line as a board ranked by [order] draws it, see [DailyBoardRow] and [GhostRow]. */
internal fun DailyBoardLine.cells(order: RankOrder, withPuzzleBoxRank: Boolean, medals: Boolean): RowCells = when (this) {
    is DailyBoardLine.Real -> row.cells(place, order, withPuzzleBoxRank, medals)
    is DailyBoardLine.Ghost -> row.cells(place, order, medals)
}

/**
 * A row between two boards: the cells it had on the board it leaves, [from], while the row itself is drawn with the
 * ones of the board it goes to, [progress] of the way there. Read only where the row is laid out and drawn.
 */
internal class RowScrub(val from: RowCells, val progress: () -> Float)

/** The scrub of the row being composed; null on a board at rest. */
internal val LocalRowScrub = staticCompositionLocalOf<RowScrub?> { null }
