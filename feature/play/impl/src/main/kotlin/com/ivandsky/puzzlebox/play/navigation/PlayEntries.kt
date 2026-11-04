package com.ivandsky.puzzlebox.play.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.navigation.Navigator
import com.ivandsky.puzzlebox.play.PlayRoute
import com.ivandsky.puzzlebox.play.ResultRoute
import com.ivandsky.puzzlebox.play.api.PlayKey
import com.ivandsky.puzzlebox.play.api.ReplayKey
import com.ivandsky.puzzlebox.play.api.ResultKey
import java.util.Base64

/** The live play route (Play tab root, or pushed by Home's "Play today"). */
fun EntryProviderScope<NavKey>.playEntry(
    navigator: Navigator,
    layout: PlayLayout,
) {
    entry<PlayKey> {
        PlayRoute(
            layout = layout,
            onBack = { navigator.leavePlay() },
            onSolved = { timeMs, hintsUsed -> navigator.solveToResult(ResultKey(timeMs, hintsUsed)) },
        )
    }
}

/** A replayed history day: the play route restores the key's base64 session blob. */
fun EntryProviderScope<NavKey>.replayEntry(
    navigator: Navigator,
    layout: PlayLayout,
) {
    entry<ReplayKey> { key ->
        PlayRoute(
            layout = layout,
            onBack = { navigator.leavePlay() },
            onSolved = { timeMs, hintsUsed -> navigator.solveToResult(ResultKey(timeMs, hintsUsed)) },
            restoredSession = runCatching {
                Base64.getDecoder().decode(key.session)
            }.getOrNull(),
        )
    }
}

/** The solved-session result; "Play again" swaps it for a fresh play entry. */
fun EntryProviderScope<NavKey>.resultEntry(navigator: Navigator) {
    entry<ResultKey> { key ->
        ResultRoute(
            timeMs = key.timeMs,
            hintsUsed = key.hintsUsed,
            onPlayAgain = { navigator.playAgainFromResult() },
        )
    }
}
