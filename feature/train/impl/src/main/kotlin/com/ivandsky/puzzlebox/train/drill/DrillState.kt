package com.ivandsky.puzzlebox.train.drill

import com.ivandsky.puzzlebox.gameui.GamePlayController
import com.ivandsky.puzzlebox.gameui.GamePlayUi
import com.ivandsky.puzzlebox.gameui.trainer.DrillCandidate

/**
 * What the drill shows. [Loading] while it picks a puzzle or fetches one; [Empty] when the technique names no puzzle at
 * all; [NeedsConnection] when none is on the device; [CantLoad] when a puzzle did not open, naming the [DrillCandidate]
 * already on the device that opens instead; [Playing] the board [controller] holds, drawn by [ui], with [won] set the
 * moment it is solved and before the solve sweep has played; [Solved] once it has.
 */
internal sealed interface DrillState {
    data object Loading : DrillState

    data object Empty : DrillState

    data object NeedsConnection : DrillState

    data class CantLoad(val alternative: DrillCandidate) : DrillState

    data class Playing(val ui: GamePlayUi, val controller: GamePlayController, val won: Boolean) : DrillState

    data object Solved : DrillState
}
