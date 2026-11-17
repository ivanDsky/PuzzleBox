package com.ivandsky.puzzlebox.compete.duel

import com.ivandsky.puzzlebox.data.match.LegRef
import com.ivandsky.puzzlebox.gameui.hidden.GivensBoard

/** What a duel leg shows as its board: the game's [Of] givens, or [None] when the puzzle is not here or the game draws none. */
internal sealed interface LegBoard {
    data class Of(val givens: GivensBoard) : LegBoard

    data object None : LegBoard
}

/** Resolves a leg's [LegBoard]; the page shows its loading state until this answers. */
internal fun interface LegBoards {
    suspend fun of(leg: LegRef): LegBoard
}

/** For a page with no boards to show. */
internal val NoLegBoards = LegBoards { LegBoard.None }
