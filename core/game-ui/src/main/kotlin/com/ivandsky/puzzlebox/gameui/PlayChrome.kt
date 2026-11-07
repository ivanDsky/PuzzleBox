package com.ivandsky.puzzlebox.gameui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.ivandsky.puzzlebox.designsystem.AccentSlot
import com.ivandsky.puzzlebox.designsystem.Spacing
import kotlinx.coroutines.delay

/*
 * The play chrome, split core | game (user ruling 2026-11-03): every game shows a title,
 * a clock, a hint control, undo/redo and a degrade banner, so those are CORE — drawn once
 * here from small seams the game's play state implements. What each seam MEANS is the
 * game's: when its clock starts, what a hint does, what an undo unit is, what Clear wipes
 * ([PlayClear]). A game's own settings (Queens' Auto-cross) ride the top bar's overflow
 * as [PlayOverflowItem]s (A14).
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
 * The hint seam. The game owns what a hint is; the chrome draws the button and the count,
 * and its caption slot shows [hintCaption] below any [PlayFeedback] message. [hintsUsed]
 * is the session's A3 count, [hintEnabled] folds the game's own gating (complete,
 * cooldown, nothing loaded), [hintCaption] is the reveal's explanation (null = none), and
 * [requestHint] runs the game's hint.
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
 * restarts), and background/foreground fold/reopen through the game's clock. The
 * lifecycle observer is registered once per screen and follows the CURRENT clock: a new
 * controller (each load) must not get the observer's synthetic on-register ON_RESUME.
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
    val currentClock by rememberUpdatedState(clock)
    DisposableEffect(lifecycleOwner) {
        // Resumed and not yet paused: leaving the composition (tab switch, push on top)
        // folds like backgrounding does, so only foreground-visible time counts (A14).
        var resumed = false
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    resumed = false
                    currentClock.onBackground()
                }
                Lifecycle.Event.ON_RESUME -> {
                    resumed = true
                    currentClock.onForeground()
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            if (resumed) currentClock.onBackground()
        }
    }
}

/**
 * The play chrome under the board every game shares (A14, tango-05, split core | game
 * 2026-11-03): the degrade banner (§6 copy passed in), the one reserved caption slot
 * ([PlayCaptionSlot] over [playCaption]: violation > rejection > hint) and the bottom
 * [PlayActionBar] (Undo · Redo · Hint · Clear). The title, timer, How to play and the
 * overflow live in the route's top bar. [clock] runs the shared clock loop here so the
 * fold follows the game's screen. Seams are nullable so a game opts out of a control it
 * does not have: no [clear] hides Clear. [accent] is the game's slot, filling Hint;
 * Neutral fits only a game without the action bar.
 */
@Composable
fun PlayChrome(
    clock: PlayClock?,
    hints: PlayHints?,
    undo: PlayUndo?,
    clear: PlayClear?,
    banner: String?,
    modifier: Modifier = Modifier,
    feedback: PlayFeedback? = null,
    accent: AccentSlot = AccentSlot.Neutral,
) {
    if (clock != null) PlayClockEffects(clock)

    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm)
            .testTag("play.side_panel"),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
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

        PlayCaptionSlot(caption = playCaption(feedback, hints))

        if (undo != null && hints != null) PlayActionBar(undo, hints, clear, accent)
    }
}
