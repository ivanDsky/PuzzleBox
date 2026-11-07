package com.ivandsky.puzzlebox.domain

/** What a friend puzzle's board shows; every state keeps the [author] pinned. */
sealed interface CustomBoardView {
    val author: AuthorPin

    /** The [top] rows, plus [mine] when this user ranks below them. */
    data class Ranked(val top: List<RankedEntry>, val mine: RankedEntry?, override val author: AuthorPin) : CustomBoardView

    /** Nobody is signed in, so the board cannot be read. */
    data class SignedOut(override val author: AuthorPin) : CustomBoardView

    /** Signed in, but the board could not be read: offline, or the read was refused. */
    data class Unavailable(override val author: AuthorPin) : CustomBoardView
}
