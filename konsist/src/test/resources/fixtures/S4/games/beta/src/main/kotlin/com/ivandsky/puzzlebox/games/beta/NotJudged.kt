package com.ivandsky.puzzlebox.games.beta

import androidx.compose.runtime.Composable

@Composable
private fun HiddenPrivate() {
}

@Composable
internal fun HiddenInternal() {
}

class Holder {
    @Composable
    fun NotTopLevel() {
    }
}
