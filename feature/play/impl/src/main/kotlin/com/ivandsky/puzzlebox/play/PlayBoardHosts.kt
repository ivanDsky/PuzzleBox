package com.ivandsky.puzzlebox.play

import androidx.compose.runtime.Composable

/**
 * [HintPolishHost] around [content]: the one host every play board and the Library lessons wrap themselves in. A graph
 * that binds none reads it as off.
 */
@Composable
internal fun PlayBoardHosts(content: @Composable () -> Unit) {
    HintPolishHost(content)
}
