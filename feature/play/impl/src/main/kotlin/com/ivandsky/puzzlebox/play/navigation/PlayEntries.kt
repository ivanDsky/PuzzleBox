package com.ivandsky.puzzlebox.play.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.ivandsky.puzzlebox.compete.api.DuelKey
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
 * the calendar (guarded against a double tap), so back returns to the library. [onOpenHistory] is unused since the library replaced the hub's
 * "Older puzzles" footer; it stays because the app's entry provider (in the app module)
 * still passes it. A [GameKey] on any stack draws its slot's live play: Up is
 * the navigator's back, and solving replaces the board with its slot's result, so back
 * from there lands on the game's origin; [GameEntry] sends a finished day to its
 * result instead. The library's Enter code pushes that code's
 * [FriendPuzzleKey] card; its Play pushes the puzzle's [CustomGameKey] board, and solving
 * replaces the board with its [CustomResultKey] result, whose Done pops back to the card and
 * whose Play again replaces it with the board again. The library opens its code sheet on
 * [codeDraft]'s text and hands it back through [onCodeDraftTaken]. A [duels] slot turns the
 * library into the Dailies, Friends and Duels tabs; without one it is the single games list.
 * [onOpenDuel] is the flag's signal for Enter code (null = off, matches are never tried); it
 * is only carried here. A duel puzzle's [MatchLegKey] draws its leg's board; Up pops, and a
 * solve, a give up or a puzzle already taken replaces it with its [MatchLegResultKey]. The library
 * acts once on [libraryRequest]'s game page or section and hands it back through [onLibraryRequestTaken].
 * The game page shows its leaderboard with [showGameBoard] and a "Your history" row calling [onOpenGameHistory];
 * [signInInPlace] puts Google sign-in on a signed-out game's side-panel times card (off = Profile's note).
 * the defaults are the page without either.
 */
fun EntryProviderScope<NavKey>.playEntry(
    navigator: Navigator,
    layout: PlayLayout,
    onOpenHistory: () -> Unit,
    codeDraft: () -> String? = { null },
    onCodeDraftTaken: () -> Unit = {},
    duels: DuelsSlot? = null,
    onOpenDuel: ((DuelKey) -> Unit)? = null,
    libraryRequest: () -> LibraryRequest? = { null },
    onLibraryRequestTaken: () -> Unit = {},
    showGameBoard: Boolean = false,
    onOpenGameHistory: (() -> Unit)? = null,
    signInInPlace: Boolean = false,
) {
    entry<PlayKey> {
        LibraryRoute(
            layout = layout,
            onPlay = { game, day -> navigator.pushOnce(GameKey(game.value, day?.toString())) },
            onOpenCode = { code -> navigator.pushOnce(FriendPuzzleKey(code.value)) },
            codeDraft = codeDraft(),
            onCodeDraftTaken = onCodeDraftTaken,
            duels = duels,
            onOpenDuel = onOpenDuel,
            request = libraryRequest(),
            onRequestTaken = onLibraryRequestTaken,
            showGameBoard = showGameBoard,
            onOpenGameHistory = onOpenGameHistory,
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
    entry<GameKey> { key -> GameEntry(key, layout, navigator, signInInPlace) }
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
 * with another game's today (17); Up and Done pop. [onOpenBoard] turns today's board block into a
 * "Full board" row handing back the result's game; null keeps the board.
 */
fun EntryProviderScope<NavKey>.resultEntry(navigator: Navigator, onOpenBoard: ((GameId) -> Unit)? = null) {
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
