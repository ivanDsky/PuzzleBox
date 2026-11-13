package com.ivandsky.puzzlebox.games.queensui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.ivandsky.puzzlebox.game.replay.ReplayFrame
import com.ivandsky.puzzlebox.gameui.replay.GameReplayBoard
import com.ivandsky.puzzlebox.gameui.replay.ReplayBoardOptions
import com.ivandsky.puzzlebox.gameui.replay.rememberStepsForward
import com.ivandsky.puzzlebox.games.queens.QueensSession

/**
 * Queens' read-only replay board: the committed board and the marks live play derives from it,
 * drawn by the live board's face. A hint shows as the replay badge on its target cells ([ReplayFrame.hinted]); no
 * selection, trace or input. A forward step while the replay plays ([ReplayBoardOptions.animate]) enters, exits and
 * changes pieces as in live play (a placed queen pops, its auto crosses grow in); paused, scrubbing, stepping back or
 * under reduced motion a frame change redraws at once. Conflict stripes show only while [ReplayBoardOptions.showErrors].
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
        val animate = rememberStepsForward(options, frame.atMs)
        // The session shown last; it moves after each composition, so this one compares against the one before it.
        val last = remember { ShownSession(session) }
        val placed = if (animate) (session.queens - last.session.queens.toSet()).singleOrNull()?.idx else null
        SideEffect { last.session = session }
        val motion = remember(session.puzzle.size) { QueensMotionState(session.puzzle.size) }
        LaunchedEffect(session) { if (placed != null) motion.pop(placed) }
        CompositionLocalProvider(
            LocalQueensReducedMotion provides !animate,
            LocalQueensMotion provides motion,
        ) {
            QueensBoardFace(model, modifier, staggerFrom = placed, hinted = frame.hinted)
        }
    }

    private class ShownSession(var session: QueensSession)
}
