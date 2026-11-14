package com.ivandsky.puzzlebox.history.ranks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.ivandsky.puzzlebox.designsystem.ScrubCell
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.gameui.board.DailyBoard
import com.ivandsky.puzzlebox.gameui.board.DailyBoardRowModel
import com.ivandsky.puzzlebox.gameui.rankRuleText

/**
 * The board slot: [slice] drawn by the shipped board kit. Rows move as they re-rank (the first
 * load climbs into place), the top three are medals, and the viewer's own row docks to the edge
 * while it scrolls out of view. A title or caption that changes (another day or board) frosts over in place.
 * With [onRowClick] another player's row is tappable. Signed out, [signIn] and [onSignIn] fill the board in place.
 * The rows let a little of the hub's wash through ([WORLD_SURFACE_ALPHA]); the viewer's own stays solid.
 *
 * With a [morph] the slot is between two boards, as far along as the morph says: the kit draws the two as one list
 * that re-ranks in place, the hint and the note under it frost from the one board's to the other's, and no row takes
 * a tap, for a row on its way is not yet the row of the board on screen.
 */
@Composable
internal fun RanksBoardSlot(
    slice: RanksBoardSlice,
    signIn: RanksSignIn,
    onSignIn: () -> Unit,
    modifier: Modifier = Modifier,
    onRowClick: ((DailyBoardRowModel) -> Unit)? = null,
    morph: RanksBoardMorph? = null,
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
            onRowClick = onRowClick.takeIf { morph == null },
            backdrop = MaterialTheme.colorScheme.surface.copy(alpha = WORLD_SURFACE_ALPHA),
            morph = morph?.board,
        )
        if (morph == null) {
            if (slice.friendsAlone) RanksFriendsHint()
            slice.note?.let { RanksBoardNote(it) }
        } else {
            if (morph.from.friendsAlone || morph.to.friendsAlone) {
                ScrubCell(morph.from.friendsAlone, morph.to.friendsAlone, morph.progress) { alone -> if (alone) RanksFriendsHint() }
            }
            if (morph.from.note != null || morph.to.note != null) {
                ScrubCell(morph.from.note, morph.to.note, morph.progress) { note -> note?.let { RanksBoardNote(it) } }
            }
        }
    }
}
