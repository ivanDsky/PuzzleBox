package com.ivandsky.puzzlebox.play

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.gameui.AdaptivePlayScaffold
import com.ivandsky.puzzlebox.gameui.PlayBoardFallback
import com.ivandsky.puzzlebox.gameui.PlayBoardPane
import com.ivandsky.puzzlebox.gameui.PlayBoardStatus
import com.ivandsky.puzzlebox.gameui.PlayLayout

/** The game's frame while its open is still being decided: the top bar over the loading board;
 *  [todaysDaily] is the key naming the game's today, the one whose copy says "today's puzzle".
 *  On two panes the loading board sits in the board pane, centred, where the loaded board lands. */
@Composable
internal fun GameLoading(layout: PlayLayout, onBack: () -> Unit, todaysDaily: Boolean = false) {
    Column {
        PlayTopBar(title = "", pastDay = false, onBack = onBack)
        val loading = PlayBoardStatus.Loading(todaysDaily)
        when (layout) {
            PlayLayout.SinglePane -> PlayBoardFallback(loading, onRetry = {}, tagPrefix = "game")
            PlayLayout.TwoPane -> AdaptivePlayScaffold(
                layout = layout,
                board = {
                    PlayBoardPane(Modifier.padding(horizontal = Spacing.lg)) {
                        PlayBoardFallback(loading, onRetry = {}, tagPrefix = "game")
                    }
                },
                sidePanel = null,
            )
        }
    }
}
