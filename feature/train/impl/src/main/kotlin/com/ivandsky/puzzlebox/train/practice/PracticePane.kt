package com.ivandsky.puzzlebox.train.practice

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ivandsky.puzzlebox.data.trainer.TechniqueProgress
import com.ivandsky.puzzlebox.designsystem.SecondaryButton
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.gameui.trainer.PracticeBoard
import com.ivandsky.puzzlebox.gameui.trainer.PracticeStatus

/**
 * A technique's practice (prototype variant C): the [chapter]'s technique name with its in-a-row [progress] and the
 * board's number as the title row, then by [state] a spinner while a board is cut or a puzzle loads, the empty message,
 * the needs-a-connection or can't-load message, the finished page, or the board. The board is the game's own ([board],
 * which draws its Spotlight itself) under one status band attached to its top edge, then the technique's idea and Show
 * me. A finished board says Done and is replaced by the next one by [state], with no tap. [showBack] adds a back arrow
 * to the title row (the phone, where the board covers the list); [onClose] is what back, the finished page's Back to
 * techniques and the empty message's action do. [onTryAgain] is the needs-a-connection action, which fetches a puzzle,
 * and [onTryAnother] the can't-load action, which opens a puzzle already on the device.
 */
@Composable
internal fun PracticePane(
    chapter: PracticeChapter,
    state: PracticeState,
    progress: TechniqueProgress,
    showBack: Boolean,
    onClose: () -> Unit,
    board: @Composable (PracticeBoard, Modifier) -> Unit,
    onTryAgain: () -> Unit,
    onTryAnother: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val name = chapter.technique.name
    Column(modifier.fillMaxSize().padding(bottom = Spacing.lg).testTag("train.practice")) {
        PracticeHeader(chapter, progress, showBack, onClose)
        Spacer(Modifier.size(Spacing.sm))
        when (state) {
            PracticeState.Loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
            PracticeState.Empty -> EmptyPage(name, onClose)
            PracticeState.NeedsConnection -> NeedsConnectionPage(name, onTryAgain)
            is PracticeState.CantLoad -> CantLoadPage(name, onTryAnother)
            PracticeState.Finished -> FinishedPage(onClose)
            // One key per board, so the next board starts from its own state.
            is PracticeState.Practicing -> key(state.board) { PracticeBody(chapter, state.board, board) }
        }
    }
}

/** One board: the status band over the game's board, the idea and Show me. */
@Composable
private fun PracticeBody(chapter: PracticeChapter, board: PracticeBoard, boardContent: @Composable (PracticeBoard, Modifier) -> Unit) {
    val status by board.status.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize()) {
        // The band and the board are one frame, centered in what the idea and Show me leave.
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            BandOverBoard(band = { StatusBand(status, chapter, onGotIt = board::gotIt) }, board = { boardContent(board, Modifier) })
        }
        Spacer(Modifier.size(Spacing.md))
        IdeaBlock(chapter.technique.line)
        Spacer(Modifier.size(Spacing.md))
        val refused = status is PracticeStatus.Easier || status is PracticeStatus.Shown || status is PracticeStatus.Done
        SecondaryButton("Show me", onClick = board::showMe, Modifier.testTag("train.showMe"), enabled = !refused)
    }
}
