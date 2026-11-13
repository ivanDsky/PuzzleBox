package com.ivandsky.puzzlebox.play.linkedin

import com.ivandsky.puzzlebox.domain.linkedin.LinkedInBoardView
import com.ivandsky.puzzlebox.domain.linkedin.LinkedInDayView
import com.ivandsky.puzzlebox.domain.linkedin.SavedBoardState
import com.ivandsky.puzzlebox.gameui.board.GhostRowModel
import com.ivandsky.puzzlebox.gameui.board.LinkedInYou

/**
 * What a day's LinkedIn data adds to a board that ranks it with the PuzzleBox rows: the connections as [ghosts], each
 * with its cached photo, and the player themself as [you] (imported result, LinkedIn name and photo); none of either
 * while there is nothing. [saved] says whether the day's board is a saved one, which the page marks with a line under the board.
 */
internal data class LinkedInBoardInput(
    val ghosts: List<GhostRowModel> = emptyList(),
    val you: LinkedInYou? = null,
    val saved: SavedBoardState = SavedBoardState.NotYetRead,
)

internal fun LinkedInDayView?.toBoardInput(): LinkedInBoardInput = LinkedInBoardInput(
    ghosts = (this?.board as? LinkedInBoardView.Shown)?.ghosts.orEmpty()
        .map { GhostRowModel(it.memberRef, it.name, it.timeMs, it.hintFree, it.photoPath) },
    you = this?.let { view ->
        val score = view.ownScore
        val owner = view.owner
        if (score == null && owner == null) null else LinkedInYou(score, owner?.name, owner?.photoPath)
    },
    saved = this?.saved ?: SavedBoardState.NotYetRead,
)
