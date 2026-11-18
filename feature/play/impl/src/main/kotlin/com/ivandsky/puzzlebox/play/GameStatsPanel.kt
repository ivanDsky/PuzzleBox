package com.ivandsky.puzzlebox.play

import android.content.Context
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ivandsky.puzzlebox.resultui.board.DailyBoardStyle

/**
 * The tablet side panel's content: [PlayStatsPanel] over [stats], under the game's
 * chrome. The top bar holds the timer.
 */
internal fun statsPanel(
    stats: PlayStatsViewModel,
    activityContext: Context,
    boardStyle: DailyBoardStyle,
): @Composable ColumnScope.() -> Unit = {
    val state by stats.state.collectAsStateWithLifecycle()
    val signIn by stats.signIn.collectAsStateWithLifecycle()
    PlayStatsPanel(
        state,
        signIn = signIn,
        onSignIn = { stats.signIn(activityContext) },
        boardStyle = boardStyle,
    )
}
