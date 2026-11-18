package com.ivandsky.puzzlebox.compete.duel

import com.ivandsky.puzzlebox.data.match.LegRef
import com.ivandsky.puzzlebox.gameui.hidden.GivensBoard

/**
 * What a duel leg shows as its board: the game's [Of] givens, or [None] when the puzzle is not here or the game draws none.
 * A friend's puzzle may instead be [TakenDown] (its author withdrew it and this device never saved it) or [Offline]
 * (not saved here and unreadable now); the page explains both, and only [Offline] is asked again.
 */
internal sealed interface LegBoard {
    data class Of(val givens: GivensBoard) : LegBoard

    data object None : LegBoard

    data object TakenDown : LegBoard

    data object Offline : LegBoard
}

/** Resolves a leg's [LegBoard]; the page shows its loading state until this answers. */
internal fun interface LegBoards {
    suspend fun of(leg: LegRef): LegBoard
}

/** For a page with no boards to show. */
internal val NoLegBoards = LegBoards { LegBoard.None }
