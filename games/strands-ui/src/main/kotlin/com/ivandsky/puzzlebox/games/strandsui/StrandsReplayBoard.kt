package com.ivandsky.puzzlebox.games.strandsui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.game.replay.ReplayFrame
import com.ivandsky.puzzlebox.gameui.replay.GameReplayBoard
import com.ivandsky.puzzlebox.games.strands.StrandsSession
import com.ivandsky.puzzlebox.games.strands.render.StrandsRendererModel

/**
 * Strands' read-only replay board: the committed board and the marks live play derives from it
 * (found words, spangram, hint outline), drawn by the live board. No trace, verdict, input or
 * motion: every frame rests, so scrubbing redraws at once.
 */
internal object StrandsReplayBoard : GameReplayBoard {
    override val pieces: String get() = "words"

    @Composable
    override fun Board(frame: ReplayFrame, modifier: Modifier) {
        // The frame is game-blind; this board is only reached through Strands' own plugin, so a miss draws nothing.
        val session = frame.session as? StrandsSession ?: return
        val model = remember(session.state) { StrandsRendererModel.of(session.state) }
        StrandsBoardContent(
            model = model,
            found = null,
            reduceMotion = true,
            trace = { Trace() },
            onTrace = {},
            // Nothing to touch, so the board shrinks to whatever space it is given.
            floor = 0.dp,
            readOnly = true,
            modifier = modifier,
        )
    }
}
