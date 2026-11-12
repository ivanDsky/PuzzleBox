package com.ivandsky.puzzlebox.games.zipui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.ivandsky.puzzlebox.game.replay.ReplayFrame
import com.ivandsky.puzzlebox.gameui.replay.GameReplayBoard
import com.ivandsky.puzzlebox.games.zip.ZipSession
import com.ivandsky.puzzlebox.games.zip.engine.ZipState
import com.ivandsky.puzzlebox.games.zip.render.ZipRendererModel

/**
 * Zip's read-only replay board: the committed trace and the marks live play derives from it,
 * drawn by the live read-only face. No hint, rejection flash, in-flight tip or input, and the
 * pipes do not animate, so a frame change redraws at once.
 */
internal object ZipReplayBoard : GameReplayBoard {
    override val pieces: String get() = "cells"

    @Composable
    override fun Board(frame: ReplayFrame, modifier: Modifier) {
        // The frame is game-blind; this board is only reached through Zip's own plugin, so a miss draws nothing.
        val session = frame.session as? ZipSession ?: return
        val model = remember(session) {
            ZipRendererModel.of(ZipState(session.puzzle, session.path), hint = null, rejection = null)
        }
        ZipBoard(model, modifier)
    }
}
