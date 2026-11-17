package com.ivandsky.puzzlebox.play.friend

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.colors

/** The right column's width (prototype 05, "Tablet"). */
internal val FriendGridRightWidth = 372.dp

/** The narrowest board column that still reads as a board: below it the page keeps the single column. */
private val FriendGridMinBoardWidth = 320.dp

private val FriendGridGap = Spacing.xl

/** The pane width that fits both columns at the page's margins: board column, gap, right column and both margins. */
internal val FriendGridMinWidth = FriendGridMinBoardWidth + FriendGridGap + FriendGridRightWidth + Spacing.lg * 2

/**
 * The friend page as two columns, for the Community tablet's right pane with CommunityPuzzles on: the board, the chips
 * and the time tiles on the left; the head, title and byline, note, primary action and ranks in a
 * [FriendGridRightWidth] column on the right. Both columns start at the top and the page scrolls as one. The Enter
 * stagger runs down the left column first, then the right. Needs [FriendCard.givens]: the caller checks [FriendCard.hiddenBoard].
 */
@Composable
internal fun FriendPuzzleGrid(
    card: FriendCard,
    entrance: CardEntrance,
    reduced: Boolean,
    onAction: () -> Unit,
    onSignIn: () -> Unit,
    onReport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = card.brand.accent.colors()
    val puzzle = card.puzzle
    Row(
        modifier.fillMaxWidth().padding(horizontal = Spacing.lg).testTag("friend.card.grid"),
        horizontalArrangement = Arrangement.spacedBy(FriendGridGap),
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
            card.givens?.let { givens -> Enter(entrance, 0, reduced) { HiddenBoardSection(givens, sharp = card.yours != null) } }
            Enter(entrance, 1, reduced) { Chips(card, accent.container, accent.onContainer, reduced) }
            Enter(entrance, 2, reduced) { Stats(card) }
        }
        Column(
            Modifier.width(FriendGridRightWidth).testTag("friend.card.grid.right"),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            Enter(entrance, 3, reduced) { FriendCardHead(card, reduced) }
            Enter(entrance, 4, reduced) { FriendTitleRow(card, onReport) }
            if (puzzle.note.isNotBlank()) Enter(entrance, 5, reduced) { Note(puzzle.note, accent.accent) }
            Enter(entrance, 6, reduced) { Action(card.action, reduced, onAction) }
            Enter(entrance, 7, reduced) {
                BoardPreview(
                    board = card.board,
                    author = puzzle.authorPin(),
                    yours = card.yours,
                    postState = puzzle.postState,
                    signIn = card.signIn,
                    onSignIn = onSignIn,
                    closed = card.takenDown,
                )
            }
        }
    }
}
