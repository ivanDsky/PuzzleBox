package com.ivandsky.puzzlebox.gameui.hidden

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import com.ivandsky.puzzlebox.game.GameSession
import com.ivandsky.puzzlebox.game.replay.ReplayFacts
import com.ivandsky.puzzlebox.game.replay.ReplayFrame
import com.ivandsky.puzzlebox.gameui.replay.GameReplayBoard
import com.ivandsky.puzzlebox.gameui.replay.ReplayBoardOptions

/**
 * A puzzle's board as it is before anyone plays it: [rows] by [cols], drawn by [content] with only what
 * the puzzle gives away at the start. The game that builds one leaves the solution behind before it builds
 * anything to draw, so nothing here can show or speak it. Read-only: no input, no verdict, no tools.
 */
@Stable
class GivensBoard(val rows: Int, val cols: Int, val content: @Composable (Modifier) -> Unit) {

    companion object {
        /**
         * [board] at the first frame of [session], a session the game built from the givens alone: no move
         * played, nothing hinted, at rest, with no error marks.
         */
        fun of(rows: Int, cols: Int, board: GameReplayBoard, session: GameSession): GivensBoard {
            val frame = ReplayFrame(
                session = session,
                before = null,
                moves = emptyList(),
                hinted = emptySet(),
                facts = ReplayFacts(hints = 0, correct = 0, total = 0, solved = false),
                atMs = 0L,
                sinceMoveMs = 0L,
                ended = false,
            )
            val options = ReplayBoardOptions(animate = false, showErrors = false)
            return GivensBoard(rows, cols) { modifier -> board.Board(frame, options, modifier) }
        }
    }
}
