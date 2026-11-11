package com.ivandsky.puzzlebox.play.linkedin

import com.ivandsky.puzzlebox.domain.linkedin.LinkedInBoardView
import com.ivandsky.puzzlebox.domain.linkedin.LinkedInDayView
import com.ivandsky.puzzlebox.gameui.board.GhostRowModel

/**
 * The connections a day's board shows, as rows to rank with the PuzzleBox board, each with its cached photo; none
 * while it is hidden or outside the window.
 */
internal fun LinkedInDayView?.toGhostRows(): List<GhostRowModel> =
    (this?.board as? LinkedInBoardView.Shown)?.ghosts.orEmpty()
        .map { GhostRowModel(it.memberRef, it.name, it.timeMs, it.hintFree, it.photoPath) }
