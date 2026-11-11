package com.ivandsky.puzzlebox.domain.linkedin

import com.ivandsky.puzzlebox.data.linkedin.LinkedInGhost

/** The connections board of one game day, as the boards draw it. */
sealed interface LinkedInBoardView {
    /** Not connected, the board has not been read yet, or the day is outside the days LinkedIn keeps a board for. */
    data object Hidden : LinkedInBoardView

    /** [ghosts] are sorted by the game's rank order, hidden times last. */
    data class Shown(val ghosts: List<LinkedInGhost>) : LinkedInBoardView
}
