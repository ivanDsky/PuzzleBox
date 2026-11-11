package com.ivandsky.puzzlebox.play.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.model.PlaySlot
import com.ivandsky.puzzlebox.navigation.Navigator
import com.ivandsky.puzzlebox.play.GameLoading
import com.ivandsky.puzzlebox.play.GameRoute
import com.ivandsky.puzzlebox.play.OpenDecision
import com.ivandsky.puzzlebox.play.PlaySessionStore
import com.ivandsky.puzzlebox.play.api.GameKey
import com.ivandsky.puzzlebox.play.api.ResultKey
import org.koin.compose.koinInject

/**
 * A [GameKey] entry: a day that already has a result opens that [ResultKey] in the key's
 * place (back lands on the game's origin, as after a solve), otherwise its board. The
 * store decides ([PlaySessionStore.decide], then [PlaySessionStore.completedResult] for a
 * solved day with no live play) before anything opens the board, so a finished day is never
 * reset by being opened; replaying is Result's "Play again".
 */
@Composable
internal fun GameEntry(key: GameKey, layout: PlayLayout, navigator: Navigator, linkedIn: Boolean = false) {
    val store = koinInject<PlaySessionStore>()
    // Frame 0 decides from what the store holds; only a solved day with no live play, or a
    // cold snapshot, waits on a read, and shows the loading frame meanwhile.
    val decided by produceState(store.decide(key), key) {
        value = when (val first = store.decide(key)) {
            OpenDecision.Open, is OpenDecision.Finished -> first
            else -> store.completedResult(key)?.let(OpenDecision::Finished) ?: OpenDecision.Open
        }
    }
    when (val state = decided) {
        OpenDecision.Open -> GameRoute(
            key = key,
            layout = layout,
            onBack = { navigator.pop() },
            onSolved = { slot, timeMs, hintsUsed ->
                val day = (slot as? PlaySlot.Daily)?.day ?: return@GameRoute
                navigator.replaceTop(ResultKey(slot.gameId.value, day.toString(), timeMs, hintsUsed))
            },
            linkedIn = linkedIn,
        )
        is OpenDecision.Finished -> {
            GameLoading(onBack = { navigator.pop() })
            LaunchedEffect(state) { navigator.replaceTop(state.result) }
        }
        else -> GameLoading(onBack = { navigator.pop() })
    }
}
