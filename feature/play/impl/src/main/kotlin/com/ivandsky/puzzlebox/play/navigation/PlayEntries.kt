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

/**
 * The live play route, the Play tab's root. Solving pushes the result on the Play stack
 * (guarded against a double fire); leaving is back, which at the root selects Home.
 */
fun EntryProviderScope<NavKey>.playEntry(
    navigator: Navigator,
    layout: PlayLayout,
) {
    entry<PlayKey> {
        PlayRoute(
            layout = layout,
            onBack = { navigator.pop() },
            onSolved = { timeMs, hintsUsed -> navigator.pushOnce(ResultKey(timeMs, hintsUsed)) },
        )
    }
}

/**
 * A replayed history day, pushed on Home's stack: the play route restores the key's base64
 * session blob; solving pushes the result above it and leaving pops back to History.
 */
fun EntryProviderScope<NavKey>.replayEntry(
    navigator: Navigator,
    layout: PlayLayout,
) {
    entry<ReplayKey> { key ->
        PlayRoute(
            layout = layout,
            onBack = { navigator.pop() },
            onSolved = { timeMs, hintsUsed -> navigator.pushOnce(ResultKey(timeMs, hintsUsed)) },
            restoredSession = runCatching {
                Base64.getDecoder().decode(key.session)
            }.getOrNull(),
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
