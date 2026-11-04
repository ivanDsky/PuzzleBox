package com.ivandsky.puzzlebox.games.beta

import androidx.compose.runtime.Composable

@Composable
fun Wrapper(content: @Composable () -> Unit) {
    content()
}
