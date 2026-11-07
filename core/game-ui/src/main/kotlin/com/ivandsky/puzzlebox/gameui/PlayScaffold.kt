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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.Spacing

/** Play screen pane arrangement. */
enum class PlayLayout { SinglePane, TwoPane }

/**
 * A4 board cap in a single pane: the widest the play board may render. It is a MAX, not a
 * fixed size — phones keep near-full width under the pane's standard margins, and a
 * shorter pane shrinks the board further so the grid aspect stays exact. A two-pane board
 * fills its pane instead ([LocalPlayBoardPaneCap]).
 */
val PlayBoardMaxWidth = 552.dp // gate round 3: user asked +15% over the original 480dp

/**
 * The two-pane board cap (A15): the board pane's side under the toolbar, less the standard
 * [playBoardPane] inset and side gutters — min(pane width, pane height − toolbar). Null in
 * a single pane and outside the scaffold (the tutorial), where each game keeps its own cap.
 */
val LocalPlayBoardPaneCap: ProvidableCompositionLocal<Dp?> = staticCompositionLocalOf { null }

/**
 * The play route's content for the two-pane side pane, under the game's own chrome (A15:
 * the route fills it with the live stats). Null, or a single pane, shows nothing.
 */
val LocalPlaySidePanel: ProvidableCompositionLocal<(@Composable ColumnScope.() -> Unit)?> =
    staticCompositionLocalOf { null }

/**
 * The board pane every game draws its board in (A14): the full pane, less a fixed inset
 * under the top bar. Each game aligns its board [Alignment.TopCenter] inside it, so the
 * board's top is the same y in every game at one window size however big its grid; the
 * space a smaller board leaves falls below it, above the caption.
 */
fun Modifier.playBoardPane(): Modifier = fillMaxSize().padding(top = Spacing.lg)

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
 * board's cap, and the side 30%, ending with the route's [LocalPlaySidePanel].
 */
@Composable
fun AdaptivePlayScaffold(
    layout: PlayLayout,
    modifier: Modifier = Modifier,
    board: @Composable BoxScope.() -> Unit,
    side: (@Composable BoxScope.() -> Unit)? = null,
    actions: (@Composable () -> Unit)? = null,
) {
    when (layout) {
        PlayLayout.SinglePane -> Column(modifier.fillMaxSize()) {
            Box(Modifier.fillMaxWidth().weight(1f), content = board)
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
                    // Unfilled weight: the route's panel measures first and the chrome takes what is left.
                    Box(Modifier.fillMaxWidth().weight(1f, fill = false), contentAlignment = Alignment.TopStart, content = side)
                }
                LocalPlaySidePanel.current?.let { panel ->
                    Column(
                        Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.sm).testTag("play.side_slot"),
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                        content = panel,
                    )
                }
            }
        }
    }
}

private const val BOARD_PANE_SHARE = 0.7f

private enum class PaneSlot { Toolbar, Board }

/**
 * The two-pane board pane: [toolbar] under the standard top inset, then [board] in the
 * height left, given its cap through [LocalPlayBoardPaneCap]. The toolbar is as wide as
 * that cap and centred, so it spans the board's columns; the cap needs the toolbar's
 * height first, hence the subcomposition.
 */
@Composable
private fun ToolbarBoardPane(
    toolbar: (@Composable () -> Unit)?,
    board: @Composable BoxScope.() -> Unit,
    modifier: Modifier,
) {
    SubcomposeLayout(modifier) { constraints ->
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val inset = Spacing.lg.roundToPx()
        val bar = if (toolbar != null) {
            subcompose(PaneSlot.Toolbar) { Box(Modifier.testTag("play.toolbar")) { toolbar() } }
        } else {
            emptyList()
        }
        val barHeight = bar.maxOfOrNull { it.maxIntrinsicHeight(width) } ?: 0
        val boardTop = if (bar.isEmpty()) 0 else inset + barHeight
        val boardHeight = (height - boardTop).coerceAtLeast(0)
        val cap = minOf(width - 2 * inset, boardHeight - inset).coerceAtLeast(0)
        val boardPlaceables = subcompose(PaneSlot.Board) {
            CompositionLocalProvider(LocalPlayBoardPaneCap provides cap.toDp()) {
                Box(Modifier.fillMaxSize(), content = board)
            }
        }.map { it.measure(Constraints.fixed(width, boardHeight)) }
        val barPlaceables = bar.map { it.measure(Constraints(minWidth = cap, maxWidth = cap, maxHeight = barHeight)) }
        layout(width, height) {
            barPlaceables.forEach { it.placeRelative(Alignment.CenterHorizontally.align(it.width, width, layoutDirection), inset) }
            boardPlaceables.forEach { it.placeRelative(0, boardTop) }
        }
    }
}
