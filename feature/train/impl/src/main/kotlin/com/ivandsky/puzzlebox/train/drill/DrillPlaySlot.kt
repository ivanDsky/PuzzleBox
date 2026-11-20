package com.ivandsky.puzzlebox.train.drill

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.ivandsky.puzzlebox.gameui.GamePlayController
import com.ivandsky.puzzlebox.gameui.GamePlayUi
import com.ivandsky.puzzlebox.gameui.PlayLayout

/**
 * The game's regular play screen for a Trainer drill, handed in by the app shell so this module needs no play edge
 * (ADR-0008). [content] draws the board `controller` holds on the screen a daily plays on, without a clock, under a
 * top bar that reads `title`, the technique's name, then `label`, which stays whole when the name is too long for the
 * bar. Its Up reports `onBack`, and `onSolved` is called once the solved board's sweep has played. It handles no
 * system back and stores nothing: the drill does both.
 */
class DrillPlaySlot(
    val content: @Composable (
        ui: GamePlayUi,
        controller: GamePlayController,
        title: String,
        label: String,
        layout: PlayLayout,
        onBack: () -> Unit,
        onSolved: () -> Unit,
        modifier: Modifier,
    ) -> Unit,
)
