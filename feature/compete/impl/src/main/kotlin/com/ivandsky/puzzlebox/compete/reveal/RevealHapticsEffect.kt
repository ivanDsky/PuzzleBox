package com.ivandsky.puzzlebox.compete.reveal

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalHapticFeedback

/**
 * Buzzes each beat the [machine] publishes while it runs. The first state collected is the one the machine starts
 * in, so a reveal that opens at its end, or is skipped, has no transition to buzz for.
 */
@Composable
internal fun RevealHapticsEffect(machine: RevealMachine) {
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(machine, haptics) {
        var previous = machine.state.value
        machine.state.collect { current ->
            RevealHaptics.between(previous, current)?.let(haptics::performHapticFeedback)
            previous = current
        }
    }
}
