package com.ivandsky.puzzlebox.history.ranks

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.testTag
import com.ivandsky.puzzlebox.designsystem.MorphCell
import com.ivandsky.puzzlebox.designsystem.ScrubCell
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.gameui.board.DailyBoard
import com.ivandsky.puzzlebox.gameui.board.DailyBoardRowModel
import com.ivandsky.puzzlebox.gameui.rankRuleText
import kotlin.math.roundToInt

// The gap over each part under the board. Inside the part, so a part that is not there leaves no gap behind.
private val UnderBoard = Modifier.padding(top = Spacing.sm)

/**
 * The board slot: [slice] drawn by the shipped board kit. Rows move as they re-rank (the first
 * load climbs into place), the top three are medals, and the viewer's own row docks to the edge
 * while it scrolls out of view. A title or caption that changes (another day or board) frosts over in place.
 * With [onRowClick] another player's row is tappable. Signed out, [signIn] and [onSignIn] fill the board in place.
 * The rows let a little of the hub's wash through ([WORLD_SURFACE_ALPHA]); the viewer's own stays solid. The note
 * sits under the last row and frosts in or out when the board gains or loses it.
 *
 * With a [morph] the slot is between two boards, as far along as the morph says: the kit draws the two as one list
 * that re-ranks in place, the hint and the note under it frost from the one board's to the other's and stay under
 * the list all the way (see [sharedHeight]), and no row takes a tap, for a row on its way is not yet the row of
 * the board on screen.
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
    Column(modifier) {
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
            if (slice.friendsAlone) RanksFriendsHint(UnderBoard)
            MorphCell(slice.note) { note -> note?.let { RanksBoardNote(it, UnderBoard) } }
        } else {
            if (morph.from.friendsAlone || morph.to.friendsAlone) {
                val height = Modifier.sharedHeight(morph.from.friendsAlone, morph.to.friendsAlone, morph.progress)
                ScrubCell(morph.from.friendsAlone, morph.to.friendsAlone, morph.progress, height) { alone ->
                    if (alone) RanksFriendsHint(UnderBoard)
                }
            }
            if (morph.from.note != null || morph.to.note != null) {
                val height = Modifier.sharedHeight(morph.from.note != null, morph.to.note != null, morph.progress)
                ScrubCell(morph.from.note, morph.to.note, morph.progress, height) { note -> note?.let { RanksBoardNote(it, UnderBoard) } }
            }
        }
    }
}

// A part under the board that only one of the two boards has takes that board's share of its height, gap included,
// so what is under it moves with the way and nothing jumps when the morph starts or lands.
private fun Modifier.sharedHeight(from: Boolean, to: Boolean, progress: () -> Float): Modifier =
    if (from == to) {
        this
    } else {
        layout { measurable, constraints ->
            val placeable = measurable.measure(constraints)
            val share = progress().coerceIn(0f, 1f).let { if (to) it else 1f - it }
            layout(placeable.width, (placeable.height * share).roundToInt()) { placeable.place(0, 0) }
        }
    }
