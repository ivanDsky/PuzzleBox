package com.ivandsky.puzzlebox.history.ranks

import com.ivandsky.puzzlebox.domain.linkedin.LinkedInBoardView
import com.ivandsky.puzzlebox.domain.linkedin.LinkedInDayView
import com.ivandsky.puzzlebox.gameui.board.GhostRowModel
import com.ivandsky.puzzlebox.gameui.board.LinkedInYou

/** What one day's LinkedIn data adds to a Friends board: the connections as [ghosts] and the viewer's own import as [you]. */
internal data class BoardGhostInput(val ghosts: List<GhostRowModel> = emptyList(), val you: LinkedInYou? = null) {
    companion object {
        val None = BoardGhostInput()
    }
}

internal fun LinkedInDayView.toGhostInput(): BoardGhostInput = BoardGhostInput(
    ghosts = (board as? LinkedInBoardView.Shown)?.ghosts.orEmpty()
        .map { GhostRowModel(it.memberRef, it.name, it.timeMs, it.hintFree, it.photoPath) },
    you = if (ownScore == null && owner == null) null else LinkedInYou(ownScore, owner?.name, owner?.photoPath),
)
