package com.ivandsky.puzzlebox.games.queensui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.ivandsky.puzzlebox.game.replay.ReplayFrame
import com.ivandsky.puzzlebox.gameui.replay.GameReplayBoard
import com.ivandsky.puzzlebox.gameui.replay.ReplayBoardOptions
import com.ivandsky.puzzlebox.games.queens.QueensSession

/**
 * Queens' read-only replay board: the committed board and the marks live play derives from it,
 * drawn by the live board's face. No hint, selection, trace or placement animation, no input. Conflict
 * stripes show only while [ReplayBoardOptions.showErrors].
 */
internal object QueensReplayBoard : GameReplayBoard {
    override val pieces: String get() = "queens"

    @Composable
    override fun Board(frame: ReplayFrame, options: ReplayBoardOptions, modifier: Modifier) {
        // The frame is game-blind; this board is only reached through Queens' own plugin, so a miss draws nothing.
        val session = frame.session as? QueensSession ?: return
        val showErrors = options.showErrors
        val model = remember(session, showErrors) {
            QueensBoardModel.of(session).let { if (showErrors) it else it.copy(violations = emptySet()) }
        }
        QueensBoardFace(model, modifier)
    }
}
