package com.ivandsky.puzzlebox.play.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.model.PlaySlot
import com.ivandsky.puzzlebox.navigation.Navigator
import com.ivandsky.puzzlebox.play.GameRoute
import com.ivandsky.puzzlebox.play.PlaySessionStore
import com.ivandsky.puzzlebox.play.api.GameKey
import com.ivandsky.puzzlebox.play.api.ResultKey
import org.koin.compose.koinInject

/**
 * A [GameKey] entry: a day that already has a result opens that [ResultKey] in the key's
 * place (back lands on the game's origin, as after a solve), otherwise its board. The
 * store decides ([PlaySessionStore.completedResult]) before anything opens the board, so
 * a finished day is never reset by being opened; replaying is Result's "Play again".
 */
@Composable
internal fun GameEntry(key: GameKey, layout: PlayLayout, navigator: Navigator) {
    val store = koinInject<PlaySessionStore>()
    val completed by produceState<Completed>(Completed.Pending, key) {
        value = store.completedResult(key)?.let(Completed::Result) ?: Completed.Open
    }
    when (val state = completed) {
        Completed.Pending -> Box(Modifier.fillMaxSize())
        is Completed.Result -> {
            Box(Modifier.fillMaxSize())
            LaunchedEffect(state) { navigator.replaceTop(state.key) }
        }
        Completed.Open -> GameRoute(
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

private sealed interface Completed {
    data object Pending : Completed
    data object Open : Completed
    data class Result(val key: ResultKey) : Completed
}
