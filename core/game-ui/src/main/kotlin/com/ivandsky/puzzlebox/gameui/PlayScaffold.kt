package com.ivandsky.puzzlebox.gameui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Play screen pane arrangement. */
enum class PlayLayout { SinglePane, TwoPane }

/**
 * A4 board cap: the widest the play board may render. It is a MAX, not a fixed size —
 * phones keep near-full width under the pane's standard margins, and a shorter pane
 * shrinks the board further so the grid aspect stays exact.
 */
val PlayBoardMaxWidth = 480.dp

/**
 * The pane decision as a pure function — same policy as home (A12 / ticket 07):
 * two-pane needs Expanded width AND landscape; a portrait pane has no height to share,
 * and medium stays single-pane per Material 3 window-size guidance.
 */
fun playLayoutFor(widthSizeClass: WindowWidthSizeClass, isLandscape: Boolean): PlayLayout =
    if (widthSizeClass == WindowWidthSizeClass.Expanded && isLandscape) {
        PlayLayout.TwoPane
    } else {
        PlayLayout.SinglePane
    }

/**
 * Adaptive play scaffold (F1 §3): the board owns the dominant pane; the side pane carries
 * the play chrome (timer, hints, game-specific panels). Single pane stacks board above
 * side; two pane places them side by side.
 */
@Composable
fun AdaptivePlayScaffold(
    layout: PlayLayout,
    modifier: Modifier = Modifier,
    board: @Composable BoxScope.() -> Unit,
    side: (@Composable BoxScope.() -> Unit)? = null,
) {
    when (layout) {
        PlayLayout.SinglePane -> Column(modifier.fillMaxSize()) {
            Box(Modifier.fillMaxWidth().weight(1f), content = board)
            if (side != null) {
                Box(Modifier.fillMaxWidth(), content = side)
            }
        }

        PlayLayout.TwoPane -> Row(modifier.fillMaxSize()) {
            Box(Modifier.weight(0.62f).fillMaxHeight(), content = board)
            VerticalDivider()
            Box(Modifier.weight(0.38f).fillMaxHeight(), contentAlignment = Alignment.TopStart) {
                if (side != null) side() else Box(Modifier)
            }
        }
    }
}
