package com.ivandsky.puzzlebox.gameui

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import com.ivandsky.puzzlebox.designsystem.AccentSlot
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.rememberReducedMotion

/** No cooldown: [LocalHintCooldown] is not provided, so presses pass straight through and nothing is remembered or run. */
private val NoCooldown = HintCooldown(window = null, now = { 0L }, pressedAt = mutableStateOf(null), sweeps = false)

/**
 * The Hint cooldown for one play screen, under [LocalHintCooldown]. The press time is saved on
 * the monotonic [now] clock, so the wait holds across recomposition and a configuration change;
 * a change of [board] (a new controller) or leaving the screen starts with none.
 */
@Composable
fun rememberHintCooldown(board: Any? = null, now: () -> Long = SystemClock::elapsedRealtime): HintCooldown {
    val window = LocalHintCooldown.current ?: return NoCooldown
    val sweeps = !rememberReducedMotion()
    val pressedAt = rememberSaveable(board) { mutableStateOf<Long?>(null) }
    val cooldown = remember(window, pressedAt, sweeps) { HintCooldown(window, now, pressedAt, sweeps) }
    val pressed = cooldown.pressedAt
    if (pressed != null) LaunchedEffect(cooldown, pressed) { cooldown.runOut() }
    return cooldown
}

// Preview: under the cooldown, Hint ready before a press, then two seconds into its wait
// (disabled, ring two fifths round).
@PuzzleBoxPreviews
@Composable
internal fun HintCooldownPreview() {
    PuzzleBoxTheme {
        CompositionLocalProvider(LocalHintCooldown provides HINT_COOLDOWN) {
            val ready = rememberHintCooldown()
            Column(Modifier.background(MaterialTheme.colorScheme.surface)) {
                PlayActionBar(
                    onUndo = {},
                    onRedo = {},
                    onHint = { ready.press {} },
                    onClear = {},
                    canUndo = true,
                    canRedo = false,
                    hintEnabled = ready.gate(enabled = true),
                    accent = AccentSlot.Teal,
                    hintRing = ready.ring,
                )
                PlayActionBar(
                    onUndo = {},
                    onRedo = {},
                    onHint = {},
                    onClear = {},
                    canUndo = true,
                    canRedo = false,
                    hintEnabled = false,
                    accent = AccentSlot.Teal,
                    hintsUsed = 1,
                    hintRing = { 0.4f },
                )
            }
        }
    }
}
