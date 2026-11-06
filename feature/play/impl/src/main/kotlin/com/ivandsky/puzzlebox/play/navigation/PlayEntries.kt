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
 * its [GameKey] (guarded against a double tap), so back returns to the hub (N2, N3);
 * "Older puzzles" is [onOpenHistory] (the app opens History on the current stack, where a
 * past day opens by date, A22). A [GameKey] on any stack draws its slot's live play: Up is
 * the navigator's back, and solving replaces the board with its slot's result, so back
 * from there lands on the game's origin (SC4).
 */
fun EntryProviderScope<NavKey>.playEntry(
    navigator: Navigator,
    layout: PlayLayout,
    onOpenHistory: () -> Unit,
) {
    entry<PlayKey> {
        HubRoute(
            layout = layout,
            onPick = { game -> navigator.pushOnce(GameKey(game.value)) },
            onOpenHistory = onOpenHistory,
        )
    }
    entry<GameKey> { key ->
        GameRoute(
            key = key,
            layout = layout,
            onBack = { navigator.pop() },
            onSolved = { slot, timeMs, hintsUsed ->
                navigator.replaceTop(ResultKey(slot.gameId.value, slot.day.toString(), timeMs, hintsUsed))
            },
        )
    }
}

/**
 * A slot's result: "Play again" replaces it with the same day's game, which opens a
 * fresh attempt (A9); Next replaces it with another game's today (17); Up and Done pop.
 */
fun EntryProviderScope<NavKey>.resultEntry(navigator: Navigator) {
    entry<ResultKey> { key ->
        ResultRoute(
            key = key,
            onPlayAgain = { navigator.replaceTop(GameKey(key.gameId, key.day)) },
            onNext = { game -> navigator.replaceTop(GameKey(game.value)) },
            onDone = { navigator.pop() },
        )
    }
}
