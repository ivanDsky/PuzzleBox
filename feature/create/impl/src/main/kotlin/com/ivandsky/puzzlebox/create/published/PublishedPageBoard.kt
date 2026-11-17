package com.ivandsky.puzzlebox.create.published

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.ivandsky.puzzlebox.create.editor.ReadOnlyBoard
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.gameui.hidden.HiddenBoard
import com.ivandsky.puzzlebox.gameui.hidden.HiddenBoardLoading

internal const val PublishedPlainBoardTag = "create.publishedPage.plainBoard"
internal const val PublishedPlainBoardLoadingTag = "create.publishedPage.plainBoard.loading"

/**
 * The board section of the author's published page. With the plain board off it is the Studio's board, read-only, with
 * its verdict, as shipped. With it on it is the draft's plain, sharp board from 126's [HiddenBoard]: no verdict, no
 * marks, no tools, the same for every game. While that board is built the room holds quiet bars; a game that cannot
 * draw one leaves the section empty, and the Studio board is never the fallback.
 */
@Composable
internal fun PublishedPageBoard(page: PublishedPuzzleViewModel.State.Ready, layout: PlayLayout, modifier: Modifier = Modifier) {
    if (!page.plainBoard) {
        ReadOnlyBoard(board = { page.ui.Board(page.controller, page.verdict, layout, it) }, modifier = modifier)
        return
    }
    val givens = page.givens
    Box(modifier, contentAlignment = Alignment.Center) {
        when {
            givens != null -> HiddenBoard(givens, sharp = true, modifier = Modifier.testTag(PublishedPlainBoardTag))
            !page.givensSettled -> HiddenBoardLoading(Modifier.testTag(PublishedPlainBoardLoadingTag))
        }
    }
}
