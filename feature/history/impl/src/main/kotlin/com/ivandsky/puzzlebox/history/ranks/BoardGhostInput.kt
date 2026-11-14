package com.ivandsky.puzzlebox.history.ranks

import com.ivandsky.puzzlebox.data.linkedin.LinkedInGhost
import com.ivandsky.puzzlebox.domain.linkedin.LinkedInBoardView
import com.ivandsky.puzzlebox.domain.linkedin.LinkedInDayView
import com.ivandsky.puzzlebox.gameui.board.GhostRowModel
import com.ivandsky.puzzlebox.gameui.board.LinkedInYou

/**
 * What one day's LinkedIn data adds to a Friends board: the connections as [ghosts], the viewer's own import as [you],
 * and the connections the board lists without a place as [unplaced] (a hidden time).
 */
internal data class BoardGhostInput(
    val ghosts: List<GhostRowModel> = emptyList(),
    val you: LinkedInYou? = null,
    val unplaced: List<GhostRowModel> = emptyList(),
) {
    companion object {
        val None = BoardGhostInput()
    }
}

internal fun LinkedInDayView.toGhostInput(unplaced: List<LinkedInGhost>): BoardGhostInput = BoardGhostInput(
    ghosts = (board as? LinkedInBoardView.Shown)?.ghosts.orEmpty().map { it.toRow() },
    you = if (ownScore == null && owner == null) null else LinkedInYou(ownScore, owner?.name, owner?.photoPath),
    unplaced = unplaced.map { it.toRow() },
)

private fun LinkedInGhost.toRow() = GhostRowModel(memberRef, name, timeMs, hintFree, photoPath)
