package com.ivandsky.puzzlebox.gameui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.Spacing

/** Play screen pane arrangement. */
enum class PlayLayout { SinglePane, TwoPane }

/**
 * A4 board cap in a single pane: the widest the play board may render. It is a MAX, not a
 * fixed size — phones keep near-full width under the pane's standard margins, and a
 * shorter pane shrinks the board further so the grid aspect stays exact. A two-pane board
 * fills its pane instead (the `paneCap` [AdaptivePlayScaffold] hands its board).
 */
val PlayBoardMaxWidth = 552.dp // gate round 3: user asked +15% over the original 480dp

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
 * Adaptive play scaffold (F1 §3, A14, A15): the board owns the dominant pane; the side pane
 * carries the game's chrome ([side]: banner and caption). [actions] is the game's control
 * row ([PlayActionBar]). Single pane stacks board, side, then actions at the bottom. Two
 * pane gives the board 70% with [actions] as a toolbar directly above it, as wide as the
 * board's cap, and the side 30%, ending with the route's [sidePanel].
 *
 * [board] gets its `paneCap`: in two panes the board pane's side under the toolbar, less
 * the [PlayBoardPane] insets and side gutters; null in a single pane, where each game keeps
 * its own cap. [sidePanel] is the route's content under the game's chrome (A15: the live
 * stats); it gets the height the chrome leaves, so it should scroll when taller. Null, or
 * a single pane, shows nothing.
 */
@Composable
fun AdaptivePlayScaffold(
    layout: PlayLayout,
    modifier: Modifier = Modifier,
    board: @Composable BoxScope.(paneCap: Dp?) -> Unit,
    side: (@Composable BoxScope.() -> Unit)? = null,
    actions: (@Composable () -> Unit)? = null,
    sidePanel: (@Composable ColumnScope.() -> Unit)?,
) {
    when (layout) {
        PlayLayout.SinglePane -> Column(modifier.fillMaxSize()) {
            Box(Modifier.fillMaxWidth().weight(1f)) { board(null) }
            if (side != null) {
                Box(Modifier.fillMaxWidth(), content = side)
            }
            if (actions != null) {
                Box(Modifier.fillMaxWidth().padding(start = Spacing.lg, end = Spacing.lg, bottom = Spacing.sm)) { actions() }
            }
        }

        PlayLayout.TwoPane -> Row(modifier.fillMaxSize()) {
            ToolbarBoardPane(actions, board, Modifier.weight(BOARD_PANE_SHARE).fillMaxHeight())
            VerticalDivider()
            Column(Modifier.weight(1f - BOARD_PANE_SHARE).fillMaxHeight().testTag("play.side_pane")) {
                if (side != null) {
                    // Unweighted: the game's chrome (captions, found words) measures first and the
                    // route's panel scrolls in what is left, so a tall panel never hides it.
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopStart, content = side)
                }
                sidePanel?.let { panel ->
                    Column(
                        Modifier.fillMaxWidth().weight(1f).padding(horizontal = Spacing.lg, vertical = Spacing.sm).testTag("play.side_slot"),
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                        content = panel,
                    )
                }
            }
        }
    }
}

private const val BOARD_PANE_SHARE = 0.7f
