package com.ivandsky.puzzlebox.games.tangoui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.game.replay.ReplayFrame
import com.ivandsky.puzzlebox.gameui.replay.GameReplayBoard
import com.ivandsky.puzzlebox.gameui.replay.ReplayBoardOptions
import com.ivandsky.puzzlebox.games.tango.TangoSession
import com.ivandsky.puzzlebox.games.tango.engine.TangoState
import com.ivandsky.puzzlebox.games.tango.render.TangoRendererModel

/**
 * Tango's read-only replay board: the committed board and the marks live play derives from it,
 * drawn by the live renderer with no input. No hint, selection, trace, rejection flash, guide
 * or placement animation. Violations settle as live play does ([tangoReplaySuppressed]).
 */
internal object TangoReplayBoard : GameReplayBoard {
    override val pieces: String get() = "cells"

    @Composable
    override fun Board(frame: ReplayFrame, options: ReplayBoardOptions, modifier: Modifier) {
        // The frame is game-blind; this board is only reached through Tango's own plugin, so a miss draws nothing.
        val session = frame.session as? TangoSession ?: return
        val before = frame.before as? TangoSession
        // Keyed on whether the window has passed, not on sinceMoveMs, so scrubbing inside it rebuilds nothing.
        val settled = frame.ended || frame.sinceMoveMs >= MotionTokens.SettleMillis
        val showErrors = options.showErrors
        val model = remember(session, before, settled, showErrors) {
            TangoRendererModel.of(
                TangoState(session.puzzle, session.grid()),
                // Errors off hides every violating set, settled or not.
                suppressedSets = if (showErrors) {
                    tangoReplaySuppressed(session, before, frame.sinceMoveMs, frame.ended)
                } else {
                    violatingSets(session.puzzle, session.grid())
                },
            )
        }
        remember { TangoBoardRenderer() }.Content(model, modifier)
    }
}

/**
 * The violating cell-sets a replay frame hides. Only a violation new since [before] is hidden,
 * and only until [MotionTokens.SettleMillis] after the move; one already in [before] shows at
 * once, and an [ended] frame shows everything. No [before] (no move yet) hides nothing.
 */
internal fun tangoReplaySuppressed(
    session: TangoSession,
    before: TangoSession?,
    sinceMoveMs: Long,
    ended: Boolean,
): Set<Set<Int>> {
    if (ended || before == null || sinceMoveMs >= MotionTokens.SettleMillis) return emptySet()
    return violatingSets(session.puzzle, session.grid()) - violatingSets(before.puzzle, before.grid())
}
