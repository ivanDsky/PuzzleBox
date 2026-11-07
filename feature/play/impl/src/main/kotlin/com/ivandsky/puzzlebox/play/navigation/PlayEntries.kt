package com.ivandsky.puzzlebox.play.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.model.PlaySlot
import com.ivandsky.puzzlebox.navigation.Navigator
import com.ivandsky.puzzlebox.play.GameRoute
import com.ivandsky.puzzlebox.play.LibraryRoute
import com.ivandsky.puzzlebox.play.ResultRoute
import com.ivandsky.puzzlebox.play.api.CustomGameKey
import com.ivandsky.puzzlebox.play.api.FriendPuzzleKey
import com.ivandsky.puzzlebox.play.api.GameKey
import com.ivandsky.puzzlebox.play.api.PlayKey
import com.ivandsky.puzzlebox.play.api.ResultKey
import com.ivandsky.puzzlebox.play.friend.FriendPuzzleRoute

/**
 * The play feature's routes. [PlayKey] is the Play tab's games library: a day card's
 * action pushes that game's [GameKey] for today, or with the day for a past one picked on
 * the calendar (guarded against a double tap), so back returns to the library (N2, N3). [onOpenHistory] is unused since the library replaced the hub's
 * "Older puzzles" footer; it stays because the app's entry provider (outside this ticket)
 * still passes it. A [GameKey] on any stack draws its slot's live play: Up is
 * the navigator's back, and solving replaces the board with its slot's result, so back
 * from there lands on the game's origin (SC4). The library's Enter code pushes that code's
 * [FriendPuzzleKey] card; its Play pushes the puzzle's [CustomGameKey] board, and solving
 * replaces the board with the card again.
 */
fun EntryProviderScope<NavKey>.playEntry(
    navigator: Navigator,
    layout: PlayLayout,
    onOpenHistory: () -> Unit,
) {
    entry<PlayKey> {
        LibraryRoute(
            layout = layout,
            onPlay = { game, day -> navigator.pushOnce(GameKey(game.value, day?.toString())) },
            onOpenCode = { code -> navigator.pushOnce(FriendPuzzleKey(code.value)) },
        )
    }
    entry<FriendPuzzleKey> { key ->
        FriendPuzzleRoute(
            key = key,
            onPlay = { game -> navigator.pushOnce(game) },
            onBack = { navigator.pop() },
        )
    }
    entry<CustomGameKey> { key ->
        GameRoute(
            key = key,
            layout = layout,
            onBack = { navigator.pop() },
            onSolved = { _, _, _ -> navigator.replaceTop(FriendPuzzleKey(key.puzzleId)) },
        )
    }
    entry<GameKey> { key ->
        GameRoute(
            key = key,
            layout = layout,
            onBack = { navigator.pop() },
            onSolved = { slot, timeMs, hintsUsed ->
                val day = (slot as? PlaySlot.Daily)?.day ?: return@GameRoute
                navigator.replaceTop(ResultKey(slot.gameId.value, day.toString(), timeMs, hintsUsed))
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
