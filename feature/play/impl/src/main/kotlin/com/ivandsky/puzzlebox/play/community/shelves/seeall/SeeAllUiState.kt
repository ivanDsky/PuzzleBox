package com.ivandsky.puzzlebox.play.community.shelves.seeall

import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.play.board.BoardSignIn
import com.ivandsky.puzzlebox.play.community.CommunityNotice
import com.ivandsky.puzzlebox.play.community.CommunityRowModel
import com.ivandsky.puzzlebox.play.community.DifficultyChoice
import com.ivandsky.puzzlebox.play.community.GameChoice

/** What the list area of a See all page shows. */
internal sealed interface SeeAllPhase {
    data object Loading : SeeAllPhase

    /** The server was not reached; Retry asks again. */
    data object Offline : SeeAllPhase

    /** The server was reached and refused the read; Retry asks again. */
    data object Refused : SeeAllPhase

    /** The [rows] that pass the query and filters, in the picked order. */
    data class Ready(val rows: List<CommunityRowModel>) : SeeAllPhase
}

/**
 * A See all page: its [scope] and what the player typed ([query]) and picked ([game], [difficulty]) over the
 * loaded puzzles, as the [phase]. [showGameFilter] is false on a game's own page, and [noMatch] is true when the query or a filter leaves nothing of a scope that has puzzles.
 * [signInPrompt] is the dialog a signed-out Report opens, with [signIn] saying how the last try went.
 */
internal data class SeeAllUiState(
    val scope: SeeAllScope? = null,
    val phase: SeeAllPhase = SeeAllPhase.Loading,
    val query: String = "",
    val game: GameId? = null,
    val difficulty: DifficultyChoice? = null,
    val games: List<GameChoice> = emptyList(),
    val showGameFilter: Boolean = true,
    val noMatch: Boolean = false,
    val signInPrompt: Boolean = false,
    val signIn: BoardSignIn = BoardSignIn.Idle,
    val notice: CommunityNotice? = null,
)
