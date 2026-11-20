package com.ivandsky.puzzlebox.train.practice

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ivandsky.puzzlebox.data.trainer.TechniqueProgress
import com.ivandsky.puzzlebox.designsystem.PrimaryButton
import com.ivandsky.puzzlebox.designsystem.SecondaryButton
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.gameui.trainer.PracticeBoard
import com.ivandsky.puzzlebox.gameui.trainer.PracticeStatus

/**
 * A technique's practice (prototype variant C): the technique's [name] with its in-a-row [progress] as the title row,
 * then by [state] a spinner while a board is cut or a puzzle loads, the empty message, the needs-a-connection or
 * can't-load message, or the board. The board is the game's own ([board], which draws its Spotlight itself) under one
 * status band attached to its top edge, then the technique's [line] (the idea) and Show me. A solved board is replaced
 * by the solved page. [showBack] adds a back arrow to the title row (the phone, where the board covers the list);
 * [onClose] is what back, the solved page's Back to techniques and the empty message's action do. [onNextBoard] is
 * the solved page's Next board, [onTryAgain] the needs-a-connection action, which fetches a puzzle, and [onTryAnother]
 * the can't-load action, which opens a puzzle already on the device.
 */
@Composable
internal fun PracticePane(
    name: String,
    line: String,
    state: PracticeState,
    progress: TechniqueProgress,
    showBack: Boolean,
    onClose: () -> Unit,
    board: @Composable (PracticeBoard, Modifier) -> Unit,
    onNextBoard: () -> Unit,
    onTryAgain: () -> Unit,
    onTryAnother: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize().padding(bottom = Spacing.lg).testTag("train.practice")) {
        PracticeHeader(name, progress, showBack, onClose)
        Spacer(Modifier.size(Spacing.sm))
        when (state) {
            PracticeState.Loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
            PracticeState.Empty -> EmptyPage(name, onClose)
            PracticeState.NeedsConnection -> NeedsConnectionPage(name, onTryAgain)
            is PracticeState.CantLoad -> CantLoadPage(name, onTryAnother)
            // One key per board, so the next board starts from its own state.
            is PracticeState.Practicing -> key(state.board) { PracticeBody(name, line, state.board, progress, board, onNextBoard, onClose) }
        }
    }
}

/** One board: the playing page until its status says solved, then the solved page. */
@Composable
private fun PracticeBody(
    name: String,
    line: String,
    board: PracticeBoard,
    progress: TechniqueProgress,
    boardContent: @Composable (PracticeBoard, Modifier) -> Unit,
    onNextBoard: () -> Unit,
    onClose: () -> Unit,
) {
    val status by board.status.collectAsStateWithLifecycle()
    if (status == PracticeStatus.Solved) {
        SolvedPage(progress, onNextBoard, onClose)
        return
    }
    Column(Modifier.fillMaxSize()) {
        // The band and the board are one frame, centered in what the idea and Show me leave.
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            BandOverBoard(band = { StatusBand(status, name, onGotIt = board::gotIt) }, board = { boardContent(board, Modifier) })
        }
        Spacer(Modifier.size(Spacing.md))
        IdeaBlock(line)
        Spacer(Modifier.size(Spacing.md))
        val explaining = status is PracticeStatus.Easier || status is PracticeStatus.Shown
        SecondaryButton("Show me", onClick = board::showMe, Modifier.testTag("train.showMe"), enabled = !explaining)
    }
}

/** The solved page: one line, how many trained moves in a row the player has, and the way on or back. */
@Composable
private fun SolvedPage(progress: TechniqueProgress, onNextBoard: () -> Unit, onClose: () -> Unit) {
    Column(
        Modifier.fillMaxSize().testTag("train.solved"),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Solved", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center)
        Text(
            summaryLine(progress),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.testTag("train.summary"),
        )
        PrimaryButton("Next board", onClick = onNextBoard, Modifier.testTag("train.nextBoard"))
        SecondaryButton("Back to techniques", onClick = onClose, Modifier.testTag("train.summary.back"))
    }
}

/** The solved page's line: the trained moves right in a row, after "Mastered" for a technique that is. */
internal fun summaryLine(progress: TechniqueProgress): String {
    val mastered = progress.mark == TechniqueProgress.Mark.Mastered
    return when {
        progress.inRow == 0 -> if (mastered) "Mastered" else "No trained moves right in a row yet"
        progress.inRow == 1 -> if (mastered) "Mastered. 1 trained move right in a row" else "1 trained move right in a row"
        else -> "${if (mastered) "Mastered. " else ""}${progress.inRow} trained moves right in a row"
    }
}
