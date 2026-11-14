package com.ivandsky.puzzlebox.history.ranks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.gameui.board.DailyBoard
import com.ivandsky.puzzlebox.gameui.board.DailyBoardRowModel
import com.ivandsky.puzzlebox.gameui.rankRuleText

/**
 * The board slot: [slice] drawn by the shipped board kit. Rows move as they re-rank (the first
 * load climbs into place), the top three are medals, and the viewer's own row docks to the edge
 * while it scrolls out of view. A title or caption that changes (another day or board) crossfades in place.
 * With [onRowClick] another player's row is tappable. Signed out, [signIn] and [onSignIn] fill the board in place.
 * The rows let a little of the hub's wash through ([WORLD_SURFACE_ALPHA]); the viewer's own stays solid.
 */
@Composable
internal fun RanksBoardSlot(
    slice: RanksBoardSlice,
    signIn: RanksSignIn,
    onSignIn: () -> Unit,
    modifier: Modifier = Modifier,
    onRowClick: ((DailyBoardRowModel) -> Unit)? = null,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        DailyBoard(
            title = slice.title,
            order = slice.order,
            state = slice.state,
            modifier = Modifier.testTag("ranks.board"),
            signIn = { RanksSignInPrompt(signIn, onSignIn) },
            animated = true,
            morphText = true,
            caption = slice.caption ?: rankRuleText(slice.order),
            medals = true,
            dock = true,
            onRowClick = onRowClick,
            backdrop = MaterialTheme.colorScheme.surface.copy(alpha = WORLD_SURFACE_ALPHA),
        )
        if (slice.friendsAlone) RanksFriendsHint()
        slice.note?.let { RanksBoardNote(it) }
    }
}
