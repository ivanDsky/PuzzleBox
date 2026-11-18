package com.ivandsky.puzzlebox.play

import androidx.compose.runtime.Composable

/**
 * [ZipDotPulseHost] and [HintPolishHost] around [content]: the one host every play board and the Library lessons
 * wrap themselves in. A graph that binds neither reads both as off.
 */
@Composable
internal fun PlayBoardHosts(content: @Composable () -> Unit) {
    ZipDotPulseHost { HintPolishHost(content) }
}
