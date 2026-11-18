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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import com.ivandsky.puzzlebox.designsystem.Spacing

/** Play screen pane arrangement. */
enum class PlayLayout { SinglePane, TwoPane }

/**
 * Adaptive play scaffold (F1 §3, A14, A15; nav-rethink B5, B6): the board owns the dominant
 * pane; the side pane carries the game's chrome ([side]: the caption). [actions] is the game's
 * controls ([PlayActionBar]). Two panes give the board 70% and the side 30%, ending with the
 * route's [sidePanel]: the route's content under the game's chrome (A15: the live stats). It
 * gets the height the chrome leaves, so it should scroll when taller. Null, or a single
 * pane, shows nothing.
 *
 * The board keeps to its device class's cap ([boardCapFor]; on a tablet also at most the cell cap times
 * [gridColumns], null: no cell cap). In a single pane [actions] sit directly under it, as wide as the board, and
 * [side] below them; two panes stack them in a column to the board's right. [board] gets its `paneCap`: the smaller
 * of that cap and what its pane leaves beside the controls ([ControlsBoardPane]).
 */
@Composable
fun AdaptivePlayScaffold(
    layout: PlayLayout,
    modifier: Modifier = Modifier,
    gridColumns: Int? = null,
    board: @Composable BoxScope.(paneCap: Dp) -> Unit,
    side: (@Composable BoxScope.() -> Unit)? = null,
    actions: (@Composable () -> Unit)? = null,
    sidePanel: (@Composable ColumnScope.() -> Unit)?,
) {
    when (layout) {
        PlayLayout.SinglePane ->
            // One centred group: the board, its controls, then the caption.
            Column(modifier.fillMaxSize(), Arrangement.Center, Alignment.CenterHorizontally) {
                val cap = boardCapFor(singlePaneDeviceClass(), gridColumns)
                ControlsBoardPane(actions, board, cap, sideColumn = false, Modifier.fillMaxWidth().weight(1f, fill = false))
                if (side != null) {
                    // No wider than the capped board and its gutters, so the caption starts where the board does.
                    Box(Modifier.widthIn(max = cap + PlayBoardInset * 2).fillMaxWidth(), content = side)
                }
            }

        PlayLayout.TwoPane -> Row(modifier.fillMaxSize()) {
            ControlsBoardPane(
                actions,
                board,
                boardCapFor(DeviceClass.TabletLandscape, gridColumns),
                sideColumn = true,
                Modifier.weight(BOARD_PANE_SHARE).fillMaxHeight(),
            )
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
