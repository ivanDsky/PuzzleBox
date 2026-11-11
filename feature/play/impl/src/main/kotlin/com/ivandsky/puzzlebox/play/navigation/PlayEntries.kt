package com.ivandsky.puzzlebox.play.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.navigation.Navigator
import com.ivandsky.puzzlebox.play.DuelsSlot
import com.ivandsky.puzzlebox.play.GameRoute
import com.ivandsky.puzzlebox.play.LibraryRequest
import com.ivandsky.puzzlebox.play.LibraryRoute
import com.ivandsky.puzzlebox.play.MatchLegResultRoute
import com.ivandsky.puzzlebox.play.PlaySessionStore
import com.ivandsky.puzzlebox.play.ResultRoute
import com.ivandsky.puzzlebox.play.api.CustomGameKey
import com.ivandsky.puzzlebox.play.api.CustomResultKey
import com.ivandsky.puzzlebox.play.api.FriendPuzzleKey
import com.ivandsky.puzzlebox.play.api.GameKey
import com.ivandsky.puzzlebox.play.api.MatchLegKey
import com.ivandsky.puzzlebox.play.api.MatchLegResultKey
import com.ivandsky.puzzlebox.play.api.PlayKey
import com.ivandsky.puzzlebox.play.api.ResultKey
import com.ivandsky.puzzlebox.play.friend.CustomResultRoute
import com.ivandsky.puzzlebox.play.friend.FriendPuzzleRoute
import org.koin.compose.koinInject

/**
 * The play feature's routes. [PlayKey] is the Play tab's games library: a day card's
 * action pushes that game's [GameKey] for today, or with the day for a past one picked on
 * the calendar (guarded against a double tap), so back returns to the library. A [GameKey] on any stack draws its slot's live play: Up is
 * the navigator's back, and solving replaces the board with its slot's result, so back
 * from there lands on the game's origin; [GameEntry] sends a finished day to its
 * result instead. The library's Enter code pushes that code's
 * [FriendPuzzleKey] card; its Play pushes the puzzle's [CustomGameKey] board, and solving
 * replaces the board with its [CustomResultKey] result, whose Done pops back to the card and
 * whose Play again replaces it with the board again. The library opens its code sheet on
 * [codeDraft]'s text and hands it back through [onCodeDraftTaken]. A [duels] slot turns the
 * library into the Dailies, Friends and Duels tabs; without one it is the single games list.
 * Its [DuelsSlot.onOpenDuel] is what the code sheet calls when an entered code names a duel (no slot: duels are off, the sheet never
 * tries a code as one). A duel puzzle's [MatchLegKey] draws its leg's board; Up pops, and a
 * solve, a give up or a puzzle already taken replaces it with its [MatchLegResultKey]. The library
 * acts once on [libraryRequest]'s game page or section and hands it back through [onLibraryRequestTaken].
 * The game page shows its leaderboard and a "Your history" row calling [onOpenGameHistory]; a signed-out
 * game's side-panel times card carries Google sign-in.
 * A non-null [onReconnectLinkedIn] turns the LinkedIn lines on the game page's day card on and is what its Reconnect opens.
 */
fun EntryProviderScope<NavKey>.playEntry(
    navigator: Navigator,
    layout: PlayLayout,
    onOpenGameHistory: () -> Unit,
    codeDraft: () -> String? = { null },
    onCodeDraftTaken: () -> Unit = {},
    duels: DuelsSlot? = null,
    libraryRequest: () -> LibraryRequest? = { null },
    onLibraryRequestTaken: () -> Unit = {},
    onReconnectLinkedIn: (() -> Unit)? = null,
) {
    entry<PlayKey> {
        LibraryRoute(
            layout = layout,
            onPlay = { game, day -> navigator.pushOnce(GameKey(game.value, day?.toString())) },
            onOpenCode = { code -> navigator.pushOnce(FriendPuzzleKey(code.value)) },
            codeDraft = codeDraft(),
            onCodeDraftTaken = onCodeDraftTaken,
            duels = duels,
            request = libraryRequest(),
            onRequestTaken = onLibraryRequestTaken,
            onOpenGameHistory = onOpenGameHistory,
            linkedIn = onReconnectLinkedIn != null,
            onReconnectLinkedIn = onReconnectLinkedIn,
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
            onSolved = { _, timeMs, hintsUsed ->
                navigator.replaceTop(CustomResultKey(key.gameId, key.puzzleId, timeMs, hintsUsed))
            },
        )
    }
    entry<CustomResultKey> { key ->
        CustomResultRoute(
            key = key,
            onPlayAgain = { navigator.replaceTop(CustomGameKey(key.gameId, key.puzzleId)) },
            onDone = { navigator.pop() },
        )
    }
    entry<GameKey> { key -> GameEntry(key, layout, navigator) }
    entry<MatchLegKey> { key ->
        GameRoute(
            key = key,
            layout = layout,
            onBack = { navigator.pop() },
            onSolved = { timeMs, hintsUsed ->
                navigator.replaceTop(MatchLegResultKey(key.code, key.legIndex, timeMs, hintsUsed, gaveUp = false))
            },
            onGaveUp = { navigator.replaceTop(MatchLegResultKey(key.code, key.legIndex, 0L, 0, gaveUp = true)) },
            onTaken = { result -> navigator.replaceTop(result) },
        )
    }
    entry<MatchLegResultKey> { key -> MatchLegResultRoute(key, navigator) }
}

/**
 * A slot's result: "Play again" starts a fresh attempt of the same day explicitly
 * ([PlaySessionStore.replay]) and replaces the result with its game; Next replaces it
 * with another game's today (17); Up and Done pop. Today's board block is a
 * "Full board" row that hands back the result's game through [onOpenBoard].
 */
fun EntryProviderScope<NavKey>.resultEntry(navigator: Navigator, onOpenBoard: (GameId) -> Unit) {
    entry<ResultKey> { key ->
        val store = koinInject<PlaySessionStore>()
        ResultRoute(
            key = key,
            onPlayAgain = {
                store.replay(key)
                navigator.replaceTop(GameKey(key.gameId, key.day))
            },
            onNext = { game -> navigator.replaceTop(GameKey(game.value)) },
            onDone = { navigator.pop() },
            onOpenBoard = onOpenBoard,
        )
    }
}
