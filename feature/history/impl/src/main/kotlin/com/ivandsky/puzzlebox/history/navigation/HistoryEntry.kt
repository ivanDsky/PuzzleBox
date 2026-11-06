package com.ivandsky.puzzlebox.history.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.ivandsky.puzzlebox.history.HistoryLayout
import com.ivandsky.puzzlebox.history.HistoryRoute
import com.ivandsky.puzzlebox.history.api.HistoryKey
import com.ivandsky.puzzlebox.navigation.Navigator
import com.ivandsky.puzzlebox.play.api.GameKey

/** History's route: a replayed day pushes [GameKey] for that (game, day) once. */
fun EntryProviderScope<NavKey>.historyEntry(
    navigator: Navigator,
    layout: HistoryLayout,
) {
    entry<HistoryKey> { key ->
        HistoryRoute(
            key = key,
            layout = layout,
            onBack = { navigator.pop() },
            onReplay = { game, day ->
                navigator.pushOnce(GameKey(game.value, day.toString()))
            },
        )
    }
}
