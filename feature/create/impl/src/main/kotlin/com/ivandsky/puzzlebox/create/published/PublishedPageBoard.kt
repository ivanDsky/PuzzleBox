package com.ivandsky.puzzlebox.create.published

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import com.ivandsky.puzzlebox.designsystem.HiddenBoardTokens
import com.ivandsky.puzzlebox.gameui.hidden.HiddenBoard
import com.ivandsky.puzzlebox.gameui.hidden.HiddenBoardLoading

internal const val PublishedPlainBoardTag = "create.publishedPage.plainBoard"
internal const val PublishedPlainBoardLoadingTag = "create.publishedPage.plainBoard.loading"

/**
 * The board section of the author's published page: the draft's plain, sharp board from 126's [HiddenBoard]: no
 * verdict, no marks, no tools, the same for every game. While that board is built the room holds quiet bars; a game
 * that cannot draw one leaves the section empty, and the Studio board is never the fallback. The plain board is at most
 * [maxSide] high, [HiddenBoardTokens.MaxSide] unless the two-column page has room for more.
 */
@Composable
internal fun PublishedPageBoard(
    page: PublishedPuzzleViewModel.State.Ready,
    modifier: Modifier = Modifier,
    maxSide: Dp = HiddenBoardTokens.MaxSide,
) {
    val givens = page.givens
    Box(modifier, contentAlignment = Alignment.Center) {
        when {
            givens != null -> HiddenBoard(givens, sharp = true, modifier = Modifier.testTag(PublishedPlainBoardTag), maxSide = maxSide)
            !page.givensSettled -> HiddenBoardLoading(Modifier.testTag(PublishedPlainBoardLoadingTag), maxSide = maxSide)
        }
    }
}
