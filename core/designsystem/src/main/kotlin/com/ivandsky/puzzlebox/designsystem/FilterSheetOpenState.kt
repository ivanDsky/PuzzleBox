package com.ivandsky.puzzlebox.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable

/**
 * Whether a filter chip's own sheet is up, saved across a rotation. [onOpenChange] hears true while the sheet is up and false when it
 * goes (or the chip does); a restored open sheet is announced again.
 */
@Composable
internal fun rememberFilterSheetOpen(onOpenChange: (Boolean) -> Unit): MutableState<Boolean> {
    val open = rememberSaveable { mutableStateOf(false) }
    val announce by rememberUpdatedState(onOpenChange)
    DisposableEffect(open.value) {
        val up = open.value
        if (up) announce(true)
        onDispose { if (up) announce(false) }
    }
    return open
}
