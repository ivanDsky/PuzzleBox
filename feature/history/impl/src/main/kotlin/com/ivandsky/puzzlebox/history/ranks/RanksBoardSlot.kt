package com.ivandsky.puzzlebox.history.ranks

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.ivandsky.puzzlebox.gameui.board.DailyBoard

/** The board slot: [slice] drawn by the shipped board kit, its rows moving as they re-rank. */
@Composable
internal fun RanksBoardSlot(slice: RanksBoardSlice, modifier: Modifier = Modifier) {
    DailyBoard(
        title = slice.title,
        order = slice.order,
        state = slice.state,
        modifier = modifier.testTag("ranks.board"),
        animated = true,
    )
}
