package com.ivandsky.puzzlebox.app

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ivandsky.puzzlebox.data.repository.UserPreferencesRepository
import com.ivandsky.puzzlebox.designsystem.GatedHapticFeedback
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import org.koin.compose.koinInject

/**
 * Provides [LocalHapticFeedback] as the device haptics behind the Vibration preference, for all of [content]. Off drops
 * every haptic the app plays, whatever a game's feel says; a flip applies to the next buzz, mid-play included. Every
 * haptic goes through [LocalHapticFeedback] (konsist S9), so this is the one gate. The device haptics are the
 * platform's, or Vibrator effects where the system drops kinds ([deviceHaptics]: One UI #1686, the light tick #1701);
 * the gate silences either.
 */
@Composable
fun VibrationGate(
    preferences: UserPreferencesRepository = koinInject(),
    content: @Composable () -> Unit,
) {
    val on by preferences.vibration.collectAsStateWithLifecycle(initialValue = true)
    VibrationGate(enabled = on, content = content)
}

/** The gate over a plain [enabled] value; the instance stays one, and each call reads the latest value. */
@Composable
internal fun VibrationGate(enabled: Boolean, content: @Composable () -> Unit) {
    val current by rememberUpdatedState(enabled)
    val platform = LocalHapticFeedback.current
    val context = LocalContext.current
    val gated = remember(platform, context) { GatedHapticFeedback(deviceHaptics(context, platform)) { current } }
    CompositionLocalProvider(LocalHapticFeedback provides gated, content = content)
}

@PuzzleBoxPreviews
@Composable
internal fun VibrationGatePreview() {
    PuzzleBoxTheme { VibrationGate(enabled = false) { Box {} } }
}
