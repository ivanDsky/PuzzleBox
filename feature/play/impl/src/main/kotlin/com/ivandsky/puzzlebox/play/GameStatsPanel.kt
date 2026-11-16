package com.ivandsky.puzzlebox.play

import android.content.Context
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ivandsky.puzzlebox.gameui.PlayClock
import com.ivandsky.puzzlebox.gameui.board.DailyBoardStyle

/**
 * The tablet side panel's content: [PlayStatsPanel] over [stats], under the game's
 * chrome, timing it off [clock] (null while the board isn't ready). [showStreakCard]
 * false leaves the panel's streak card out.
 */
internal fun statsPanel(
    stats: PlayStatsViewModel,
    clock: PlayClock?,
    activityContext: Context,
    boardStyle: DailyBoardStyle,
    showStreakCard: Boolean,
): @Composable ColumnScope.() -> Unit = {
    val state by stats.state.collectAsStateWithLifecycle()
    val signIn by stats.signIn.collectAsStateWithLifecycle()
    PlayStatsPanel(
        state,
        elapsedMs = { clock?.displayedElapsedMs() },
        signIn = signIn,
        onSignIn = { stats.signIn(activityContext) },
        boardStyle = boardStyle,
        showStreakCard = showStreakCard,
    )
}
