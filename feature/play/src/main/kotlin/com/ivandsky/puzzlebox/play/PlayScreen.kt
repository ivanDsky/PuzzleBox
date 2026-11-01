package com.ivandsky.puzzlebox.play

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.gameui.AdaptivePlayScaffold
import com.ivandsky.puzzlebox.gameui.BoardError
import com.ivandsky.puzzlebox.gameui.BoardLoading
import com.ivandsky.puzzlebox.gameui.DragTraceBoard
import com.ivandsky.puzzlebox.gameui.GameUiRegistry
import com.ivandsky.puzzlebox.gameui.GridCell
import com.ivandsky.puzzlebox.gameui.HintButton
import com.ivandsky.puzzlebox.gameui.HintController
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.gameui.PlayBoardMaxWidth
import com.ivandsky.puzzlebox.gameui.TimerBar
import com.ivandsky.puzzlebox.games.zip.render.ZipRendererModel
import kotlinx.coroutines.delay

/**
 * The Zip play screen (ticket 13): board + trace gesture + timer + hints, arranged by
 * the 07 adaptive policy (two-pane needs Expanded width AND landscape). State flows one
 * way: [state] owns the session; the screen draws and forwards events.
 *
 * Ticket 23: the win moment is a ROUTE handoff, not an overlay — when the session
 * completes, [onSolved] carries the frozen A3 facts to the result route; this screen
 * never renders win content itself.
 */
@Composable
fun PlayScreen(
    state: ZipPlayState,
    layout: PlayLayout,
    registry: GameUiRegistry,
    onBack: () -> Unit,
    onSolved: (timeMs: Long, hintsUsed: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Timer readout ticks ONLY while a window is actually running (F2 §2: active
    // foreground play); pause/background ends the loop, foreground restarts it.
    val runningSince = state.session?.runningSince
    if (runningSince != null) {
        LaunchedEffect(runningSince) {
            while (state.session?.runningSince != null) {
                state.tick()
                delay(200)
            }
        }
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> state.onBackground()
                Lifecycle.Event.ON_RESUME -> state.onForeground()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val ready = state.screen as? ZipScreen.Ready
    val model = remember(state.screen, state.session, state.hintCell, state.rejection) {
        state.rendererModel()
    }

    // The win moment (ticket 23): a one-shot route handoff — the frozen facts go to the
    // result route while the record write keeps running in the flow, never awaited here.
    val completed = state.session?.takeIf { it.isComplete }
    LaunchedEffect(completed) {
        if (completed != null) onSolved(completed.elapsedMs, completed.hintsUsed)
    }

    Box(modifier.fillMaxSize()) {
        AdaptivePlayScaffold(
            layout = layout,
            modifier = Modifier.fillMaxSize(),
            board = { BoardArea(state, ready, model) },
            side = { SidePanel(state, ready) },
        )
    }
}

@Composable
private fun BoxScope.BoardArea(
    state: ZipPlayState,
    ready: ZipScreen.Ready?,
    model: ZipRendererModel?,
) {
    Box(
        Modifier
            .fillMaxSize()
            .testTag("play.board_area")
            // A4: standard pane margins; the cap below is a max, not a fixed width.
            .padding(horizontal = Spacing.lg),
        contentAlignment = Alignment.Center,
    ) {
        val screenState = state.screen
        when {
            ready != null && model != null -> DragTraceBoard(
                rows = model.n,
                cols = model.n,
                path = model.path.map { GridCell(it / model.n, it % model.n) },
                hooks = remember(ready.puzzle) { ZipDragHooks(ready.puzzle.board) },
                onEvent = state::onDragEvent,
                edgeOverlays = zipWallOverlays(model),
                cellContent = { row, col -> ZipCellDecor(model, row, col) },
                // A4: capped at PlayBoardMaxWidth and centered by the pane; BoardGrid's
                // aspectRatio keeps the grid exact under these loose constraints. The
                // outline rides the same modifier (U1 §4 — Zip's outline is gridline-weight).
                modifier = Modifier
                    .widthIn(max = PlayBoardMaxWidth)
                    .heightIn(max = PlayBoardMaxWidth)
                    .zipBoardOutline(model.n)
                    .testTag("play.board"),
            )

            screenState is ZipScreen.Unavailable -> BoardError(
                message = screenState.message,
                onRetry = state::load,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Spacing.xl)
                    .testTag("play.error"),
            )

            else -> BoardLoading(modifier = Modifier.testTag("play.loading"))
        }
    }
}

@Composable
private fun SidePanel(
    state: ZipPlayState,
    ready: ZipScreen.Ready?,
) {
    val complete = state.session?.isComplete == true
    // Single source of truth for the count is the session (A3); the controller re-arms
    // whenever that count changes, and its recordHint() is never double-booked.
    val hints = remember(state.session?.hintsUsed ?: 0) { HintController(state.session?.hintsUsed ?: 0) }

    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg, vertical = Spacing.md)
            .testTag("play.side_panel"),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "Zip",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = subtitle(ready),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            HintButton(
                hints = hints,
                onHint = state::hint,
                enabled = ready != null && !complete,
            )
        }

        ready?.degraded?.let { error ->
            Surface(
                color = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("play.degrade_banner"),
            ) {
                Text(
                    text = degradeBannerMessage(error),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
                )
            }
        }

        TimerBar(elapsedMs = state.displayedElapsedMs())

        Spacer(Modifier.padding(bottom = Spacing.xs))
    }
}

private fun subtitle(ready: ZipScreen.Ready?): String = when {
    ready == null -> "Daily"
    else -> ready.ref?.puzzleNumber?.let { number -> "Daily · #$number" } ?: "Daily"
}
