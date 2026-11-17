package com.ivandsky.puzzlebox.play.friend

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.isSpecified
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.TwoColumnMinWidth
import com.ivandsky.puzzlebox.designsystem.maxContentWidth
import com.ivandsky.puzzlebox.gameui.hidden.HiddenBoardLoading

/**
 * The board's slot while the code opens: the quiet pulsing bars in the card's column, centred as the spinner is. On a
 * pane [paneHeight] high wide enough for [FriendPuzzleGrid] it is that page's skeleton, [FriendGridSkeleton], instead.
 */
@Composable
internal fun FriendLoadingBoard(modifier: Modifier = Modifier, paneHeight: Dp = Dp.Unspecified) {
    if (!paneHeight.isSpecified) {
        SingleLoadingBoard(modifier)
        return
    }
    BoxWithConstraints(modifier.fillMaxSize()) {
        if (maxWidth >= TwoColumnMinWidth) FriendGridSkeleton(paneHeight) else SingleLoadingBoard(Modifier)
    }
}

@Composable
private fun SingleLoadingBoard(modifier: Modifier) {
    Box(modifier.fillMaxSize().maxContentWidth().padding(horizontal = Spacing.lg), contentAlignment = Alignment.Center) {
        HiddenBoardLoading(Modifier.testTag("friend.card.loading.board"))
    }
}
