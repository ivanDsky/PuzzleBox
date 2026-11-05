package com.ivandsky.puzzlebox.play.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.navigation.Navigator
import com.ivandsky.puzzlebox.play.GameRoute
import com.ivandsky.puzzlebox.play.HubRoute
import com.ivandsky.puzzlebox.play.ResultRoute
import com.ivandsky.puzzlebox.play.api.GameKey
import com.ivandsky.puzzlebox.play.api.PlayKey
import com.ivandsky.puzzlebox.play.api.ResultKey

/**
 * The play feature's routes. [PlayKey] is the Play tab's games hub: picking a game pushes
 * its [GameKey] (guarded against a double tap), so back returns to the hub (N2, N3). A
 * [GameKey] on any stack draws its slot's live play: Up is the navigator's back, and
 * solving replaces the board with its result.
 */
fun EntryProviderScope<NavKey>.playEntry(
    navigator: Navigator,
    layout: PlayLayout,
) {
    entry<PlayKey> {
        HubRoute(onPick = { game -> navigator.pushOnce(GameKey(game.value)) })
    }
    entry<GameKey> { key ->
        GameRoute(
            key = key,
            layout = layout,
            onBack = { navigator.pop() },
            onSolved = { timeMs, hintsUsed -> navigator.replaceTop(ResultKey(timeMs, hintsUsed)) },
        )
    }
}

/**
 * The solved-session result; "Play again" pops back to the play entry beneath it, whose
 * session the result route has already reset to a fresh play of the same puzzle.
 */
fun EntryProviderScope<NavKey>.resultEntry(navigator: Navigator) {
    entry<ResultKey> { key ->
        ResultRoute(
            timeMs = key.timeMs,
            hintsUsed = key.hintsUsed,
            onPlayAgain = { navigator.pop() },
        )
    }
}
