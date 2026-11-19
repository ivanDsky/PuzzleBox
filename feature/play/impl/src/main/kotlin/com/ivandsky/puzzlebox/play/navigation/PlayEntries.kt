package com.ivandsky.puzzlebox.play.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.navigation.Navigator
import com.ivandsky.puzzlebox.play.DuelPrefill
import com.ivandsky.puzzlebox.play.DuelsSlot
import com.ivandsky.puzzlebox.play.GameRoute
import com.ivandsky.puzzlebox.play.LibraryRequest
import com.ivandsky.puzzlebox.play.LibraryRoute
import com.ivandsky.puzzlebox.play.MatchLegResultRoute
import com.ivandsky.puzzlebox.play.PlaySessionStore
import com.ivandsky.puzzlebox.play.ReplayCompareRoute
import com.ivandsky.puzzlebox.play.ReplayRoute
import com.ivandsky.puzzlebox.play.ResultRoute
import com.ivandsky.puzzlebox.play.TrainSlot
import com.ivandsky.puzzlebox.play.api.CustomGameKey
import com.ivandsky.puzzlebox.play.api.CustomResultKey
import com.ivandsky.puzzlebox.play.api.FriendPuzzleKey
import com.ivandsky.puzzlebox.play.api.GameKey
import com.ivandsky.puzzlebox.play.api.MatchLegKey
import com.ivandsky.puzzlebox.play.api.MatchLegResultKey
import com.ivandsky.puzzlebox.play.api.PlayKey
import com.ivandsky.puzzlebox.play.api.ReplayCompareKey
import com.ivandsky.puzzlebox.play.api.ReplayKey
import com.ivandsky.puzzlebox.play.api.ResultKey
import com.ivandsky.puzzlebox.play.friend.CustomResultRoute
import com.ivandsky.puzzlebox.play.friend.FriendPuzzleRoute
import com.ivandsky.puzzlebox.resultui.board.DailyBoardStyle
import java.time.LocalDate
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
 * library into the Dailies and Duels tabs, and a [train] slot adds Train after them; without a [duels] one it is the single games list.
 * Its [DuelsSlot.onOpenDuel] is what the code sheet calls when an entered code names a duel (no slot: duels are off, the sheet never
 * tries a code as one). A duel puzzle's [MatchLegKey] draws its leg's board; Up pops, and a
 * solve, a give up or a puzzle already taken replaces it with its [MatchLegResultKey]. The library
 * acts once on [libraryRequest]'s game page or section and hands it back through [onLibraryRequestTaken].
 * The game page shows its rank as a summary row calling [onOpenRanks] with the game and its day; a signed-out
 * game's side-panel times card carries Google sign-in.
 * A [ReplayKey] draws that solve's replay and a [ReplayCompareKey] two solves' replays side by side; Up pops.
 * A non-null [onReconnectLinkedIn] turns the LinkedIn lines on the game page's day card on and is what its Reconnect opens.
 * [onOpenRanks] also opens the Ranks hub from the Result's rank chip, and the marks of the play side panel's times
 * draw as chips beside the name, as the hub's boards do. [onMakeOne] adds "Make one" to each game page.
 * The friend puzzles and Custom's list live on the app's Community tab ([communityEntry]): Games shows Dailies and
 * Duels, and Train with a [train] slot.
 */
fun EntryProviderScope<NavKey>.playEntry(
    navigator: Navigator,
    layout: PlayLayout,
    onOpenRanks: (GameId, LocalDate?) -> Unit,
    codeDraft: () -> String? = { null },
    onCodeDraftTaken: () -> Unit = {},
    duels: DuelsSlot? = null,
    train: TrainSlot? = null,
    libraryRequest: () -> LibraryRequest? = { null },
    onLibraryRequestTaken: () -> Unit = {},
    onReconnectLinkedIn: (() -> Unit)? = null,
    onMakeOne: (GameId) -> Unit,
) {
    entry<PlayKey> {
        LibraryRoute(
            layout = layout,
            onPlay = { game, day -> navigator.pushOnce(GameKey(game.value, day?.toString())) },
            onOpenCode = { code -> navigator.pushOnce(FriendPuzzleKey(code.value)) },
            codeDraft = codeDraft(),
            onCodeDraftTaken = onCodeDraftTaken,
            duels = duels,
            train = train,
            request = libraryRequest(),
            onRequestTaken = onLibraryRequestTaken,
            linkedIn = onReconnectLinkedIn != null,
            onReconnectLinkedIn = onReconnectLinkedIn,
            onOpenRanks = onOpenRanks,
            onMakeOne = onMakeOne,
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
            onOpenReplay = { navigator.pushOnce(it) },
        )
    }
    entry<GameKey> { key ->
        GameEntry(
            key,
            layout,
            navigator,
            linkedIn = onReconnectLinkedIn != null,
            boardStyle = DailyBoardStyle.defaults(inlineMarks = true),
        )
    }
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
    entry<ReplayKey> { key -> ReplayRoute(key, onBack = { navigator.pop() }) }
    entry<ReplayCompareKey> { key -> ReplayCompareRoute(key, onBack = { navigator.pop() }) }
}

/**
 * A slot's result: "Play again" starts a fresh attempt of the same day explicitly
 * ([PlaySessionStore.playAgain]) and replaces the result with its game; Next replaces it
 * with another game's today (17); Up and Done pop.
 * [linkedIn] adds the day's LinkedIn connections to the rank row. The player's rank is a chip that calls [onOpenRanks]
 * with the game and the result's day. [onChallenge] is what Result's Challenge row and
 * primary call with the friend to challenge and the game ([DuelPrefill]); the app answers it by opening Games' Duels tab
 * with that prefill. A missed day and a friend's or community puzzle replace the result with that day's or that
 * puzzle's board, as Next does.
 */
fun EntryProviderScope<NavKey>.resultEntry(
    navigator: Navigator,
    onOpenRanks: (GameId, LocalDate?) -> Unit,
    linkedIn: Boolean = false,
    onChallenge: (DuelPrefill) -> Unit = {},
) {
    entry<ResultKey> { key ->
        val store = koinInject<PlaySessionStore>()
        ResultRoute(
            key = key,
            onPlayAgain = {
                store.playAgain(key)
                navigator.replaceTop(GameKey(key.gameId, key.day))
            },
            onNext = { game -> navigator.replaceTop(GameKey(game.value)) },
            onDone = { navigator.pop() },
            onOpenReplay = { navigator.pushOnce(it) },
            linkedIn = linkedIn,
            onOpenRanks = onOpenRanks,
            onChallenge = onChallenge,
            onOpenDay = { game, day -> navigator.replaceTop(GameKey(game.value, day.toString())) },
            onOpenPuzzle = { code -> navigator.replaceTop(FriendPuzzleKey(code.value)) },
        )
    }
}
