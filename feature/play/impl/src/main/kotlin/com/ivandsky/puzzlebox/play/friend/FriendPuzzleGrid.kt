package com.ivandsky.puzzlebox.play.friend

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import com.ivandsky.puzzlebox.designsystem.colors

/**
 * The friend page as two columns, for the Community tablet's right pane: the board, the chips
 * and the time tiles on the left; the head, title and byline, note, primary action and ranks in a
 * [com.ivandsky.puzzlebox.designsystem.TwoColumnRightWidth] column on the right ([FriendGridFrame]). The board fills the left column and, on a pane
 * [paneHeight] high, grows only as far as keeps the board, chips and tiles in view; the columns are centred, on each
 * other and on the pane. The Enter stagger runs down the left column first, then the right. Needs [FriendCard.givens]:
 * the caller checks [FriendCard.hiddenBoard].
 */
@Composable
internal fun FriendPuzzleGrid(
    card: FriendCard,
    entrance: CardEntrance,
    reduced: Boolean,
    paneHeight: Dp,
    onAction: () -> Unit,
    onSignIn: () -> Unit,
    onReport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = card.brand.accent.colors()
    val puzzle = card.puzzle
    FriendGridFrame(
        paneHeight = paneHeight,
        tag = "friend.card.grid",
        modifier = modifier,
        board = { side ->
            card.givens?.let { givens -> Enter(entrance, 0, reduced) { HiddenBoardSection(givens, sharp = card.yours != null, maxSide = side) } }
        },
        below = {
            Enter(entrance, 1, reduced) { Chips(card, accent.container, accent.onContainer, reduced) }
            Enter(entrance, 2, reduced) { Stats(card) }
        },
        right = {
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
        },
    )
}
