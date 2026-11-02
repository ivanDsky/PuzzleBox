package com.ivandsky.puzzlebox.play

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
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
import com.ivandsky.puzzlebox.gameui.rememberReducedMotion
import com.ivandsky.puzzlebox.gameui.TimerBar
import com.ivandsky.puzzlebox.games.zip.render.ZipRendererModel
import kotlinx.coroutines.delay

/** Ticket 28: the hint arrow's on-screen timing — hold ~2s, then a short fade. */
private const val HINT_ARROW_HOLD_MS = 2000L
private const val HINT_ARROW_FADE_MS = 300

/**
 * The shared play-screen clock (F2 §2: active foreground play only) — the tick loop
 * runs while a running window is open ([PlayClock.clockWindow] keys it, so each new
 * window restarts), and background/foreground fold/reopen through the state.
 */
@Composable
internal fun PlayClockEffects(clock: PlayClock) {
    val window = clock.clockWindow
    if (window != null) {
        LaunchedEffect(window) {
            while (clock.clockWindow === window) {
                clock.tick()
                delay(200)
            }
        }
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> clock.onBackground()
                Lifecycle.Event.ON_RESUME -> clock.onForeground()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
}

/**
 * The play screen's Zip branch (ticket 13): board + trace gesture + timer + hints,
 * arranged by the 07 adaptive policy (two-pane needs Expanded width AND landscape).
 * State flows one way: [state] owns the session; the screen draws and forwards events.
 *
 * Ticket 23: the win moment is a ROUTE handoff, not an overlay — when the session
 * completes, [onSolved] carries the frozen A3 facts to the result route; this screen
 * never renders win content itself. Tango plays through [TangoPlayScreen] with the
 * same shell (tango-05).
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
    PlayClockEffects(state)

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
            side = {
                PlaySidePanel(
                    title = "Zip",
                    subtitle = subtitle(ready),
                    banner = ready?.degraded?.let(::degradeBannerMessage),
                    elapsedMs = state.displayedElapsedMs(),
                    hintsUsed = state.session?.hintsUsed ?: 0,
                    hintEnabled = ready != null && state.session?.isComplete != true,
                    onHint = state::hint,
                )
            },
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
            ready != null && model != null -> {
                val reducedMotion = rememberReducedMotion()
                // Ticket 28: the hint arrow lives ~2s at the auto-placed head (or until
                // the next input, which nulls it), fading out unless motion is reduced.
                val arrow = state.hintArrow
                val arrowAlpha = remember(arrow) { Animatable(1f) }
                LaunchedEffect(arrow) {
                    if (arrow == null) return@LaunchedEffect
                    delay(HINT_ARROW_HOLD_MS)
                    if (!reducedMotion) {
                        arrowAlpha.animateTo(0f, tween(HINT_ARROW_FADE_MS))
                    }
                    state.clearHintArrow()
                }
                Box {
                    DragTraceBoard(
                        rows = model.n,
                        cols = model.n,
                        path = model.path.map { GridCell(it / model.n, it % model.n) },
                        hooks = remember(ready.puzzle) { ZipDragHooks(ready.puzzle.board) },
                        onEvent = state::onDragEvent,
                        edgeOverlays = zipWallOverlays(model),
                        underlay = { ZipPipes(model) },
                        cellContent = { row, col ->
                            ZipCellDecor(model, row, col, animatePulse = !reducedMotion)
                        },
                        // A4: capped at PlayBoardMaxWidth and centered by the pane; BoardGrid's
                        // aspectRatio keeps the grid exact under these loose constraints. The
                        // outline rides the same modifier (U1 §4 — Zip's outline is gridline-weight).
                        modifier = Modifier
                            .widthIn(max = PlayBoardMaxWidth)
                            .heightIn(max = PlayBoardMaxWidth)
                            .zipBoardOutline(model.n)
                            .testTag("play.board"),
                    )
                    arrow?.let { HintArrowHead(it, model, arrowAlpha.value, Modifier.matchParentSize()) }
                }
            }

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

/**
 * The play chrome both games share (tango-05): title/subtitle voice, degrade banner
 * (§6 exact copy passed in), the record-of-truth timer, and the hint control
 * (§5/§9: side-panel controls ≥ 48 dp — HintButton carries its own floor).
 */
@Composable
internal fun PlaySidePanel(
    title: String,
    subtitle: String,
    banner: String?,
    elapsedMs: Long,
    hintsUsed: Int,
    hintEnabled: Boolean,
    onHint: () -> Unit,
) {
    // Single source of truth for the count is the session (A3); the controller re-arms
    // whenever that count changes, and its recordHint() is never double-booked.
    val hints = remember(hintsUsed) { HintController(hintsUsed) }

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
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            HintButton(
                hints = hints,
                onHint = onHint,
                enabled = hintEnabled,
            )
        }

        banner?.let { message ->
            Surface(
                color = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("play.degrade_banner"),
            ) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
                )
            }
        }

        TimerBar(elapsedMs = elapsedMs)

        Spacer(Modifier.padding(bottom = Spacing.xs))
    }
}

private fun subtitle(ready: ZipScreen.Ready?): String = when {
    ready == null -> "Daily"
    else -> ready.ref?.puzzleNumber?.let { number -> "Daily · #$number" } ?: "Daily"
}
