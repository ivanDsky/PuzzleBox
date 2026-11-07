package com.ivandsky.puzzlebox.create.editor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.ivandsky.puzzlebox.designsystem.ContainerTransform
import com.ivandsky.puzzlebox.designsystem.rememberReducedMotion
import com.ivandsky.puzzlebox.model.ShareCode
import kotlinx.coroutines.delay

/**
 * Whether [code]'s ticket has its code and author time stamped in. Only a ticket that [stamps]
 * (its code was just published here) starts unlanded and lands once the card has grown in;
 * every other showing starts landed, and once landed it stays so for that code while its saved
 * state lives, so recomposing, a restored instance or reopening the puzzle never stamps again.
 * Reduced motion lands at once.
 */
@Composable
internal fun rememberTicketLanded(code: ShareCode, stamps: Boolean): Boolean {
    val reduced = rememberReducedMotion()
    var landed by rememberSaveable(code.value) { mutableStateOf(!stamps) }
    LaunchedEffect(code.value) {
        if (landed) return@LaunchedEffect
        if (!reduced) delay(ContainerTransform.enterDurationMillis.toLong())
        landed = true
    }
    return landed
}
