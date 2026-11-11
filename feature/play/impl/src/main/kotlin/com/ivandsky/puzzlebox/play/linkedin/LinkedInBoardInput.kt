package com.ivandsky.puzzlebox.play.linkedin

import com.ivandsky.puzzlebox.domain.linkedin.LinkedInBoardView
import com.ivandsky.puzzlebox.domain.linkedin.LinkedInDayView
import com.ivandsky.puzzlebox.game.BestScore
import com.ivandsky.puzzlebox.gameui.board.GhostRowModel

/**
 * What a day's LinkedIn data adds to a board that ranks it with the PuzzleBox rows: the connections as [ghosts], each
 * with its cached photo, and the player's own imported result as [own]; none of either while there is nothing.
 */
internal data class LinkedInBoardInput(val ghosts: List<GhostRowModel> = emptyList(), val own: BestScore? = null)

internal fun LinkedInDayView?.toBoardInput(): LinkedInBoardInput = LinkedInBoardInput(
    ghosts = (this?.board as? LinkedInBoardView.Shown)?.ghosts.orEmpty()
        .map { GhostRowModel(it.memberRef, it.name, it.timeMs, it.hintFree, it.photoPath) },
    own = this?.ownScore,
)
