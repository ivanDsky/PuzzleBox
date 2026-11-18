package com.ivandsky.puzzlebox.play

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import com.ivandsky.puzzlebox.gameui.GamePlayController
import com.ivandsky.puzzlebox.gameui.GamePlayUi
import com.ivandsky.puzzlebox.gameui.PlayClock

/**
 * Draws the [RulesSheet] over the live board while [copy] is non-null, and holds the clock still under it: the
 * controller's [PlayClock] is folded when the sheet opens and again whenever its window reopens beneath it (the app
 * resumed). When the sheet leaves it resumes the clock only while the board is [playing], so Replay the tutorial
 * never reopens a clock the tutorial then folds again.
 */
@Composable
internal fun RulesSheetHost(
    ui: GamePlayUi,
    controller: GamePlayController,
    copy: RulesSheetCopy?,
    playing: Boolean,
    onDismiss: () -> Unit,
    onReplayTutorial: () -> Unit,
) {
    // Read before the early return: the sheet's dispose runs in the same pass that sees `playing` change.
    val playingNow by rememberUpdatedState(playing)
    if (copy == null) return
    val clock = controller as? PlayClock
    LaunchedEffect(clock, clock?.clockWindow) { clock?.onBackground() }
    DisposableEffect(clock) { onDispose { if (playingNow) clock?.onForeground() } }
    RulesSheet(copy, ui.brand, onDismiss, onReplayTutorial)
}
