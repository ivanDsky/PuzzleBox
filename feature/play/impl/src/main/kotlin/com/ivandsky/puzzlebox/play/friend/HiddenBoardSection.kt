package com.ivandsky.puzzlebox.play.friend

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.maxContentWidth
import com.ivandsky.puzzlebox.gameui.hidden.GivensBoard
import com.ivandsky.puzzlebox.gameui.hidden.HiddenBoard
import com.ivandsky.puzzlebox.gameui.hidden.HiddenBoardLoading

/**
 * The puzzle's board on its page, centred in the card's column at the slot's own size: blurred under "Hidden until you
 * play" until [sharp] (you have solved it), then as it is. Only the [givens] reach it, never the solution. Tagged by
 * state, `friend.card.board.hidden` or `friend.card.board.sharp`, for the tests.
 */
@Composable
internal fun HiddenBoardSection(givens: GivensBoard, sharp: Boolean, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        HiddenBoard(
            board = givens,
            sharp = sharp,
            modifier = Modifier.testTag(if (sharp) "friend.card.board.sharp" else "friend.card.board.hidden"),
        )
    }
}

/** The board's slot while the code opens: the quiet pulsing bars in the card's column, centred as the spinner is. */
@Composable
internal fun FriendLoadingBoard(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().maxContentWidth().padding(horizontal = Spacing.lg), contentAlignment = Alignment.Center) {
        HiddenBoardLoading(Modifier.testTag("friend.card.loading.board"))
    }
}
