package com.ivandsky.puzzlebox.history.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.ivandsky.puzzlebox.history.HistoryLayout
import com.ivandsky.puzzlebox.history.HistoryRoute
import com.ivandsky.puzzlebox.history.api.HistoryKey
import com.ivandsky.puzzlebox.history.api.HistoryRootKey
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.navigation.Navigator
import com.ivandsky.puzzlebox.play.api.GameKey
import java.time.LocalDate

/**
 * History's routes: the pushed [HistoryKey] with a back arrow, and the tab root
 * [HistoryRootKey] without one (a game's link carries its game and day in the key). A play-again
 * day pushes [GameKey] for that (game, day) once.
 */
fun EntryProviderScope<NavKey>.historyEntry(
    navigator: Navigator,
    layout: HistoryLayout,
) {
    val playAgain: (GameId, LocalDate) -> Unit = { game, day ->
        navigator.pushOnce(GameKey(game.value, day.toString()))
    }
    val openReplay: (NavKey) -> Unit = { navigator.pushOnce(it) }
    entry<HistoryKey> { key ->
        HistoryRoute(
            key = key,
            layout = layout,
            onBack = { navigator.pop() },
            onPlayAgain = playAgain,
            onOpenReplay = openReplay,
        )
    }
    entry<HistoryRootKey> {
        HistoryRoute(
            key = HistoryKey(),
            layout = layout,
            onBack = null,
            onPlayAgain = playAgain,
            onOpenReplay = openReplay,
        )
    }
}
