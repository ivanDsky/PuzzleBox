package com.ivandsky.puzzlebox.domain.linkedin

import com.ivandsky.puzzlebox.data.linkedin.LinkedInGhost

/** The connections board of one game day, as the boards draw it. */
sealed interface LinkedInBoardView {
    /** No board is saved for the day: it was never read, or not yet. */
    data object Hidden : LinkedInBoardView

    /** [ghosts] are sorted by the game's rank order, hidden times last. */
    data class Shown(val ghosts: List<LinkedInGhost>) : LinkedInBoardView
}
