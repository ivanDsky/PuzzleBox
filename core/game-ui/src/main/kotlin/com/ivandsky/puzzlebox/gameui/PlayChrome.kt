package com.ivandsky.puzzlebox.gameui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.ivandsky.puzzlebox.designsystem.Spacing
import kotlinx.coroutines.delay

/*
 * The play chrome, split core | game (user ruling 2026-11-03): every game shows a title,
 * a clock, a hint control, undo/redo and a degrade banner, so those are CORE — drawn once
 * here from three small seams the game's play state implements. What each seam MEANS is
 * the game's: when its clock starts, what a hint does, what an undo unit is. Anything
 * truly game-specific (Queens' Auto-cross, Clear, conflict caption) rides the
 * [PlayChrome] `gameControls` slot.
 */

/**
 * The clock seam (F2 §2: active foreground play only; the loop lives in
 * [PlayClockEffects]). [clockWindow] is a per-window identity that changes each time the
 * running window (re)opens — null while paused/not started. The game owns the semantics:
 * when the first window opens, what a background fold banks, what counts as elapsed.
 */
interface PlayClock {
    val clockWindow: Any?
    fun tick()
    fun onBackground()
    fun onForeground()
    fun displayedElapsedMs(): Long
}

/**
 * The hint seam. The game owns what a hint is; the chrome draws the button, the count and
 * the caption. [hintsUsed] is the session's A3 count, [hintEnabled] folds the game's own
 * gating (complete, cooldown, nothing loaded), [hintCaption] is the reveal's explanation
 * (null = none), and [requestHint] runs the game's hint.
 */
interface PlayHints {
    val hintsUsed: Int
    val hintEnabled: Boolean
    val hintCaption: String?
    fun requestHint()
}

/** The undo/redo seam over the game's command history (core [com.ivandsky.puzzlebox.game.CommandHistory]). */
interface PlayUndo {
    val canUndo: Boolean
    val canRedo: Boolean
    fun undo()
    fun redo()
}

/** Tick period of the shared clock loop. */
private const val CLOCK_TICK_MS = 200L

/**
 * The shared play-screen clock (F2 §2: active foreground play only) — the tick loop runs
 * while a running window is open ([PlayClock.clockWindow] keys it, so each new window
 * restarts), and background/foreground fold/reopen through the game's clock.
 */
@Composable
fun PlayClockEffects(clock: PlayClock) {
    val window = clock.clockWindow
    if (window != null) {
        LaunchedEffect(window) {
            while (clock.clockWindow === window) {
                clock.tick()
                delay(CLOCK_TICK_MS)
            }
        }
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, clock) {
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
 * The play chrome every game shares (tango-05, split core | game 2026-11-03): title /
 * subtitle voice, the hint control with its caption, the degrade banner (§6 copy passed
 * in), the record-of-truth timer, and undo/redo (§5/§9: controls ≥ 48 dp — the M3
 * buttons carry their own floor). Seams are nullable so a game opts out of a control it
 * does not have; [gameControls] is the game-specific remainder.
 */
@Composable
fun PlayChrome(
    title: String,
    subtitle: String,
    clock: PlayClock?,
    hints: PlayHints?,
    undo: PlayUndo?,
    banner: String?,
    modifier: Modifier = Modifier,
    gameControls: @Composable ColumnScope.() -> Unit = {},
) {
    if (clock != null) PlayClockEffects(clock)

    Column(
        modifier
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
            if (hints != null) {
                HintButton(
                    count = hints.hintsUsed,
                    onHint = hints::requestHint,
                    enabled = hints.hintEnabled,
                )
            }
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

        hints?.hintCaption?.let { caption ->
            Text(
                text = caption,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("play.hint_caption"),
            )
        }

        if (clock != null) TimerBar(elapsedMs = clock.displayedElapsedMs())

        if (undo != null) UndoRedoRow(undo)

        gameControls()
    }
}

/** Undo / Redo over the game's command history — enabled exactly when the history can move. */
@Composable
fun UndoRedoRow(undo: PlayUndo, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedButton(
            onClick = undo::undo,
            enabled = undo.canUndo,
            modifier = Modifier.testTag("play.undo"),
        ) { Text("Undo") }
        OutlinedButton(
            onClick = undo::redo,
            enabled = undo.canRedo,
            modifier = Modifier.testTag("play.redo"),
        ) { Text("Redo") }
    }
}
