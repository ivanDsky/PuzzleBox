package com.ivandsky.puzzlebox.play

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import com.ivandsky.puzzlebox.gameui.PlayBoardFallback
import com.ivandsky.puzzlebox.gameui.PlayBoardStatus

/** The game's frame while its open is still being decided: the top bar over the loading board. */
@Composable
internal fun GameLoading(onBack: () -> Unit) {
    Column {
        PlayTopBar(title = "", pastDay = false, onBack = onBack)
        PlayBoardFallback(PlayBoardStatus.Loading, onRetry = {}, tagPrefix = "game")
    }
}
