package com.ivandsky.puzzlebox.play.community

import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.play.board.BoardSignIn

/** What the New today list shows below its header. */
internal sealed interface CommunityPhase {
    data object Loading : CommunityPhase

    /** [rows] newest first; empty when nothing new was published today (or every one is hidden here). */
    data class Loaded(val rows: List<CommunityRowModel>) : CommunityPhase

    /** The server was not reached; Retry asks again. */
    data object Offline : CommunityPhase

    /** The server was reached and refused the read; Retry asks again. */
    data object Refused : CommunityPhase
}

/** A line for the snackbar; [id] tells two equal lines apart so the second shows too. */
internal data class CommunityNotice(val id: Int, val message: String)

/**
 * The New today section. [game] and
 * [difficulty] are the filters picked (null is All, and Any difficulty); [signInPrompt] is the sign-in dialog a
 * signed-out Report opens, with [signIn] saying how the last try went. [filterSheet] is how the filter is reached: a
 * filter icon above the list that opens a sheet ([com.ivandsky.puzzlebox.play.PlayFlag.CommunityFilterSheet]), or
 * false, the head's ⋮ menu.
 */
internal data class CommunityState(
    val games: List<GameChoice> = emptyList(),
    val game: GameId? = null,
    val difficulty: DifficultyChoice? = null,
    val phase: CommunityPhase = CommunityPhase.Loading,
    val signInPrompt: Boolean = false,
    val signIn: BoardSignIn = BoardSignIn.Idle,
    val notice: CommunityNotice? = null,
    val filterSheet: Boolean = false,
)
