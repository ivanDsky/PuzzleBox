package com.ivandsky.puzzlebox.history.ranks

import com.ivandsky.puzzlebox.domain.linkedin.ObserveLinkedInDayView
import com.ivandsky.puzzlebox.domain.ranks.RankScope
import com.ivandsky.puzzlebox.domain.ranks.RankWindow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * The LinkedIn ghosts of the board on screen (A15), read from what the phone already holds: owner-only local data,
 * so no Firestore read and no sync. Only a game's Day board in Friends scope has any; every other board, and a hub
 * built without LinkedIn ([linkedIn] null), gets none and touches nothing.
 */
internal class BoardGhosts(private val linkedIn: ObserveLinkedInDayView?) {

    fun observe(board: RanksBoard, scope: RankScope, window: RankWindow): Flow<BoardGhostInput> {
        if (linkedIn == null || scope != RankScope.Friends || board !is RanksBoard.Game || window !is RankWindow.Day) {
            return flowOf(BoardGhostInput.None)
        }
        return linkedIn.observed(ObserveLinkedInDayView.Target(board.id, window.anchor)).map { it.toGhostInput() }.distinctUntilChanged()
    }
}
